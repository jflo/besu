/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.BonsaiLayeredWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.TrieLogManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapServer;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage.AccountRangeData;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public class SnapServerAccountsTest {

  private WorldStateStorage worldStateStorage;

  private final Blockchain blockchain = mock(Blockchain.class);

  private WorldStateArchive archive;

  private final EthMessages inboundHandlers = new EthMessages();

  private final Bytes32 Bytes32_MAX =
      Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  @Before
  public void setUp() {
    when(blockchain.observeBlockAdded(any())).thenReturn(1l);

    StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
    worldStateStorage = new BonsaiWorldStateKeyValueStorage(storageProvider);
    final Map<Bytes32, BonsaiLayeredWorldState> layeredWorldStatesByHash = new HashMap<>();

    archive =
        new BonsaiWorldStateArchive(
            new TrieLogManager(
                blockchain,
                (BonsaiWorldStateKeyValueStorage) worldStateStorage,
                12,
                layeredWorldStatesByHash),
            storageProvider,
            blockchain);
  }

  @Test
  public void serverStartup() {
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    assertThat(server).isNotNull();
  }

  @Test
  public void handleRequestForMaxRangeResponseFits() {
    Address addr1 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f31");
    Address addr2 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f32");
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    this.archive.getMutable().updater().createAccount(addr1, 1, Wei.ONE);
    this.archive.getMutable().updater().createAccount(addr2, 2, Wei.of(2));
    this.archive.getMutable().persist(null);
    Hash worldstateRoot = this.archive.getMutable().rootHash();

    GetAccountRangeMessage req =
        GetAccountRangeMessage.readFrom(
            GetAccountRangeMessage.create(
                worldstateRoot, Bytes32.ZERO, Bytes32_MAX, AbstractSnapMessageData.SIZE_REQUEST));

    assertThat(req.getRootHash()).isNotEmpty();
    assertThat(req.getRootHash().get()).isEqualTo(this.archive.getMutable().rootHash());

    AccountRangeMessage resp =
        AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));

    assertThat(resp).isNotNull();
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isNotEmpty();
    assertThat(accountRangeData.accounts()).hasSize(2);
    assertThat(accountRangeData.accounts().get(Hash.hash(addr1))).isNotNull();
    Bytes account1Content = accountRangeData.accounts().get(Hash.hash(addr1));
    Bytes account2Content = accountRangeData.accounts().get(Hash.hash(addr2));

    assertThat(account2Content).isNotNull();
    assertThat(account1Content).isNotNull();
    assertThat(accountRangeData.accounts().get(Hash.hash(addr2))).isNotNull();

    StateTrieAccountValue account1Trie =
        StateTrieAccountValue.readFrom(new BytesValueRLPInput(account1Content, false));
    StateTrieAccountValue account2Trie =
        StateTrieAccountValue.readFrom(new BytesValueRLPInput(account2Content, false));

    assertThat(account1Trie.getNonce()).isEqualTo(1);
    assertThat(account1Trie.getBalance()).isEqualTo(Wei.ONE);
    assertThat(account2Trie.getNonce()).isEqualTo(2);
    assertThat(account2Trie.getBalance()).isEqualTo(Wei.of(2));

    WorldStateProofProvider proofProvider = new WorldStateProofProvider(this.worldStateStorage);
    boolean proven =
        proofProvider.isValidRangeProof(
            accountRangeData.accounts().firstKey(),
            accountRangeData.accounts().lastKey(),
            worldstateRoot,
            accountRangeData.proofs(),
            accountRangeData.accounts());
    assertThat(proven).isTrue();
  }

  @Test
  public void truncatesToMaxAccountListSize() {
    Bytes addressBase = Bytes.fromHexString("0x10000000000000000000000000000000000000");

    TreeMap<Bytes32, Bytes> accounts = new TreeMap<>();
    for (int i = 1; i < 21; i++) {
      Address lastAccount = Address.wrap(Bytes.concatenate(addressBase, Bytes.of(i)));
      accounts.put(Hash.hash(lastAccount), lastAccount);
      this.archive.getMutable().updater().createAccount(lastAccount, i, Wei.of(i));
    }
    this.archive.getMutable().persist(null);
    Hash worldstateRoot = this.archive.getMutable().rootHash();
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);

    GetAccountRangeMessage req =
        GetAccountRangeMessage.readFrom(
            GetAccountRangeMessage.create(
                worldstateRoot,
                accounts.firstKey(),
                accounts.lastKey(),
                BigInteger.valueOf(
                    10 * 102))); // 102 bytes per account, 32 for hash, 70 for account data

    assertThat(req.getRootHash()).isNotEmpty();
    assertThat(req.getRootHash().get()).isEqualTo(this.archive.getMutable().rootHash());

    AccountRangeMessage resp =
        AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));

    assertThat(resp).isNotNull();
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isNotEmpty();
    assertThat(accountRangeData.accounts()).hasSize(10);

    Object[] hashOrder = accounts.keySet().toArray();

    WorldStateProofProvider proofProvider = new WorldStateProofProvider(this.worldStateStorage);
    boolean proven =
        proofProvider.isValidRangeProof(
            (Bytes32) hashOrder[0],
            (Bytes32) hashOrder[9],
            worldstateRoot,
            accountRangeData.proofs(),
            accountRangeData.accounts());
    assertThat(proven).isTrue();
  }

  @Test
  public void returnsEmptyIfWorldStateNotFound() {
    Address addr1 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f31");
    Address addr2 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f32");
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    this.archive.getMutable().updater().createAccount(addr1, 1, Wei.ONE);
    this.archive.getMutable().persist(null);
    Hash firstWorldStateRoot = this.archive.getMutable().rootHash();
    this.archive.getMutable().updater().createAccount(addr2, 2, Wei.of(2));
    this.archive.getMutable().persist(null);

    GetAccountRangeMessage req =
        GetAccountRangeMessage.readFrom(
            GetAccountRangeMessage.create(
                firstWorldStateRoot, Bytes32.ZERO, Bytes32_MAX, BigInteger.valueOf(2)));

    assertThat(req.getRootHash()).isNotEmpty();
    assertThat(req.getRootHash().get()).isEqualTo(firstWorldStateRoot);

    AccountRangeMessage resp =
        AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isEmpty();
    assertThat(accountRangeData.proofs()).isEmpty();
  }

  // The responding node is allowed to return less data than requested (own QoS limits),
  // but the node must return at least one account. If no accounts exist between startingHash and
  // limitHash, then the first (if any) account after limitHash must be provided.
  // https://github.com/ethereum/devp2p/blob/master/caps/snap.md#getaccountrange-0x00

  @Test
  public void returnsStartHereOnEmptyRange() {
    // create  account, add to world state.
    Address toFind = Address.fromHexString("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    this.archive.getMutable().updater().createAccount(toFind, 31337, Wei.of(100));
    this.archive.getMutable().persist(null);
    Hash rangeLimit = Hash.hash(toFind);
    BigInteger upperLimit = rangeLimit.copy().toBigInteger().subtract(BigInteger.ONE);

    // request a range from 0 to that accounts hash - 1
    GetAccountRangeMessage req =
        GetAccountRangeMessage.readFrom(
            GetAccountRangeMessage.create(
                this.archive.getMutable().rootHash(),
                Bytes32.ZERO,
                Bytes32.wrap(upperLimit.toByteArray()),
                AbstractSnapMessageData.SIZE_REQUEST));

    // assert that account is returned, and is the only one.
    AccountRangeMessage resp =
        AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isNotEmpty();
    assertThat(accountRangeData.proofs()).isNotEmpty();
    assertThat(accountRangeData.accounts()).hasSize(1);

    Bytes accountSlim = accountRangeData.accounts().get(Hash.hash(toFind));
    StateTrieAccountValue cowContent =
        StateTrieAccountValue.readFrom(new BytesValueRLPInput(accountSlim, false));
    assertThat(cowContent.getNonce()).isEqualTo(31337);
    assertThat(cowContent.getBalance()).isEqualTo(Wei.of(100));

    // The responding node must Merkle prove the starting hash (even if it does not exist)
    // and the last returned account (if any exists after the starting hash).

    WorldStateProofProvider proofProvider = new WorldStateProofProvider(this.worldStateStorage);
    boolean proven =
        proofProvider.isValidRangeProof(
            Bytes32.ZERO,
            Hash.hash(toFind),
            this.archive.getMutable().rootHash(),
            accountRangeData.proofs(),
            accountRangeData.accounts());
    assertThat(proven).isTrue();
  }

  @Test
  public void noProofsOnAllAccounts() {
    // currently Geth returns proofs when a range is fully satisfied, contrary to spec.
    // todo: implement test coverage once spec is clarified.
  }

  // TODO add tests for old worldstates going back 128 blocks

}
