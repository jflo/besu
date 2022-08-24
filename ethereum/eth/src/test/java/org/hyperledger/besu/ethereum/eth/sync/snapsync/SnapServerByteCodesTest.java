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


import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiLayeredWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.TrieLogManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapServer;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetByteCodesMessage;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.account.Account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public class SnapServerByteCodesTest {

  private WorldStateStorage worldStateStorage;

  private final Blockchain blockchain = mock(Blockchain.class);

  private WorldStateArchive archive;

  private final EthMessages inboundHandlers = new EthMessages();

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
  public void getsAllByteCodes() {

    BlockDataGenerator genny = new BlockDataGenerator();

    List<Account> contracts =
        genny.createRandomContractAccountsWithNonEmptyStorage(this.archive.getMutable(), 20);

    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);

    // todo: talk to Karim about renaming to refer to account/address hashes instead of code hashes.
    // The later is confusing because it implies a hash of the bytecode, when what is stored in
    // world
    // state is actually indexed by hash of address.

    GetByteCodesMessage req =
        GetByteCodesMessage.create(
            contracts.stream().map(a -> Hash.hash(a.getAddress())).collect(Collectors.toList()));

    req = GetByteCodesMessage.readFrom(req);

    assertThat(req.getRootHash()).isEmpty();
    ByteCodesMessage resp =
        ByteCodesMessage.readFrom(server.constructGetBytecodesResponse(this.archive, req));
    assertThat(resp).isNotNull();
    //    assertThat(resp.bytecodes(true).codes()).hasSize(20);
    // assert that response has keys in order of contracts list.
    for (int i = 0; i < 20; i++) {
      assertThat((contracts.get(i).getCode())).isEqualTo(resp.bytecodes(false).codes().get(i));
    }
  }

  // Returns a number of requested contract codes.
  // The order is the same as in the request, but there might be gaps if not all codes are
  // available or there might be fewer is QoS limits are reached.
  @Test
  public void getsMostByteCodes() {
    BlockDataGenerator genny = new BlockDataGenerator();
    List<Account> valid = genny.createRandomContractAccountsWithNonEmptyStorage(this.archive.getMutable(), 20);
    List<Bytes32> missing = List.of(Bytes32.random(), Bytes32.random(), Bytes32.random());

    List<Bytes32> requested = valid.stream().map(a -> Hash.hash(a.getAddress())).collect(Collectors.toList());
    requested.add(0, missing.get(0));
    requested.add(missing.get(2));
    requested.add(13, missing.get(1)); //0th, last and 13th should be gaps in response

    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);

    GetByteCodesMessage req = GetByteCodesMessage.create(requested);
    req = GetByteCodesMessage.readFrom(req);
    assertThat(req.getRootHash()).isEmpty();
    ByteCodesMessage resp = ByteCodesMessage.readFrom(server.constructGetBytecodesResponse(this.archive, req));
    assertThat(resp).isNotNull();
    ArrayDeque<Bytes> byteCodes = resp.bytecodes(false).codes();
    assertThat(byteCodes).hasSize(23);
    assertThat(byteCodes.last()).isEqualTo(Bytes.EMPTY); //Are empty bytes the correct way to represent gaps?
    byteCodes.removeLast();
    assertThat(byteCodes.first()).isEqualTo(Bytes.EMPTY);
    byteCodes.removeFirst();
    assertThat(byteCodes.get(12)).isEqualTo(Bytes.EMPTY);
    byteCodes.remove(12);
    assertThat(byteCodes.containsAll(valid.stream().map(a -> a.getCode()).collect(Collectors.toList()))).isTrue();

  }
}
