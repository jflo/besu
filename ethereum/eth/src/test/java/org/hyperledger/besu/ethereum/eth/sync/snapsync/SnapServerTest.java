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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt256Value;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.BonsaiLayeredWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.TrieLogManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapServer;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage.AccountRangeData;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;

public class SnapServerTest {


  private WorldStateStorage worldStateStorage;

  private final Blockchain blockchain = mock(Blockchain.class);

  private WorldStateArchive archive;

  private final EthMessages inboundHandlers = new EthMessages();

  private final Bytes32 Bytes32_MAX = Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  @Before
  public void setUp() {
    when(blockchain.observeBlockAdded(any())).thenReturn(1l);

      StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
      worldStateStorage =
          new BonsaiWorldStateKeyValueStorage(storageProvider);
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
  public void handleRequestForMaxRange() {
    Address addr1 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f31");
    Address addr2 = Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f32");
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    this.archive.getMutable().updater().createAccount(addr1, 1, Wei.ONE);
    this.archive.getMutable().updater().createAccount(addr2, 2, Wei.of(2));
    this.archive.getMutable().persist(null);
    Hash worldstateRoot = this.archive.getMutable().rootHash();

    GetAccountRangeMessage req = GetAccountRangeMessage.readFrom(GetAccountRangeMessage.create(
        worldstateRoot,
        Bytes32.ZERO,
        Bytes32_MAX));

    assertThat(req.getRootHash()).isNotEmpty();
    assertThat(req.getRootHash().get()).isEqualTo(this.archive.getMutable().rootHash());

    AccountRangeMessage resp = AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));

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

    StateTrieAccountValue account1Trie = StateTrieAccountValue.readFrom(new BytesValueRLPInput(account1Content, false));
    StateTrieAccountValue account2Trie = StateTrieAccountValue.readFrom(new BytesValueRLPInput(account2Content, false));

    assertThat(account1Trie.getNonce()).isEqualTo(1);
    assertThat(account1Trie.getBalance()).isEqualTo(Wei.ONE);
    assertThat(account2Trie.getNonce()).isEqualTo(2);
    assertThat(account2Trie.getBalance()).isEqualTo(Wei.of(2));

    assertThat(accountRangeData.proofs()).isNotEmpty();


    assertThat(accountRangeData.proofs()).hasSize(4); //TODO but why?
    WorldStateProofProvider proofProvider = new WorldStateProofProvider(this.worldStateStorage);
    boolean proven = proofProvider.isValidRangeProof(Bytes32.ZERO, Bytes32_MAX, worldstateRoot, accountRangeData.proofs(), accountRangeData.accounts());
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

    GetAccountRangeMessage req = GetAccountRangeMessage.readFrom(GetAccountRangeMessage.create(
        firstWorldStateRoot,
        Bytes32.ZERO,
        Bytes32_MAX));

    assertThat(req.getRootHash()).isNotEmpty();
    assertThat(req.getRootHash().get()).isEqualTo(firstWorldStateRoot);

    AccountRangeMessage resp = AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isEmpty();
    assertThat(accountRangeData.proofs()).isEmpty();
  }

  @Test
  public void returnsFirstAfterLimitWhenOutOfRange() {
    //create  account, add to world state.
    Address toFind = Address.fromHexString("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    this.archive.getMutable().updater().createAccount(toFind, 31337, Wei.of(100));
    this.archive.getMutable().persist(null);
    Hash rangeLimit = Hash.hash(toFind);
    BigInteger upperLimit = rangeLimit.copy().toBigInteger().subtract(BigInteger.ONE);

    //request a range from 0 to that accounts hash - 1
    GetAccountRangeMessage req = GetAccountRangeMessage.readFrom(GetAccountRangeMessage.create(
        this.archive.getMutable().rootHash(),
        Bytes32.ZERO,
        Bytes32.wrap(upperLimit.toByteArray())
        ));

    //assert that account is returned, and is the only one.
    AccountRangeMessage resp = AccountRangeMessage.readFrom(server.constructGetAccountRangeResponse(this.archive, req));
    AccountRangeData accountRangeData = resp.accountData(false);
    assertThat(accountRangeData.accounts()).isNotEmpty();
    assertThat(accountRangeData.proofs()).isNotEmpty();
    assertThat(accountRangeData.accounts()).hasSize(1);
    assertThat(accountRangeData.proofs()).hasSize(2);
    Bytes accountSlim = accountRangeData.accounts().get(Hash.hash(toFind));
    StateTrieAccountValue cowContent = StateTrieAccountValue.readFrom(new BytesValueRLPInput(accountSlim, false));
    assertThat(cowContent.getNonce()).isEqualTo(31337);
    assertThat(cowContent.getBalance()).isEqualTo(Wei.of(100));

  }

  @Test
  public void mustMerkleProveStartingHashAndLastReturned() {
    //even if the starting one doesn't exist.
    //add 2 accounts, search for them by exact range, check the proofs for each.

  }

  @Test
  public void merkleProveMissingStartHashAndLastReturned() {

  }

  @Test
  public void noProofsOnAllAccounts() {

  }

  //TODO add tests for old worldstates going back 128 blocks

  @Test
  public void handleRequestForTrieNodes() {

  }

}
