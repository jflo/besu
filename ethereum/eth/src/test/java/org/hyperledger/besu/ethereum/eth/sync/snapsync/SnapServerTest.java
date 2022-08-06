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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.bonsai.BonsaiLayeredWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.TrieLogManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapServer;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;

@RunWith(Parameterized.class)
public class SnapServerTest {


  private final DataStorageFormat storageFormat;
  private WorldStateStorage worldStateStorage;

  private final Blockchain blockchain = mock(Blockchain.class);

  private WorldStateArchive archive;

  private final EthMessages inboundHandlers = new EthMessages();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  public SnapServerTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setUp() {
    when(blockchain.observeBlockAdded(any())).thenReturn(1l);
    if (storageFormat == DataStorageFormat.BONSAI) {
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
    } else {
      worldStateStorage = new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    }
  }

  @Test
  public void serverStartup() {
    SnapServer server = new SnapServer(this.inboundHandlers, this.archive);
    assertThat(server).isNotNull();
  }

}
