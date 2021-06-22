/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Hexadecimals;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.testutil.BlockTestUtil;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByNumberLatestDesyncIntegrationTest {

  private static JsonRpcMethod ethGetBlockNumber;
  private static Long latestFullySyncdBlockNumber = 0L;
  private static Long latestFastSyncdBlockNumber = 0L;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);
    final BlockchainImporter importer =
        new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson);

    final BlockDataGenerator gen = new BlockDataGenerator();
    final WorldStateArchive state =
        InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive();
    // TODO: run same test with coverage of Bonsai state?

    importer.getGenesisState().writeStateTo(state.getMutable());
    final Block genesis = importer.getGenesisBlock();
    final MutableBlockchain blockchain =
        InMemoryKeyValueStorageProvider.createInMemoryBlockchain(importer.getGenesisBlock());
    final ProtocolContext ether = new ProtocolContext(blockchain, state, null);
    final ProtocolSchedule protocolSchedule = importer.getProtocolSchedule();

    final ProtocolSpec genesisSpec =
            protocolSchedule.getByBlockNumber(genesis.getHeader().getNumber());
    final BlockImporter genesisSpecBlockImporter = genesisSpec.getBlockImporter();
    //genesisSpecBlockImporter.fastImportBlock(ether, genesis, gen.receipts(genesis),
      //      HeaderValidationMode.LIGHT_SKIP_DETACHED, HeaderValidationMode.LIGHT_SKIP_DETACHED);
    genesisSpecBlockImporter.importBlock(ether, genesis, HeaderValidationMode.FULL, HeaderValidationMode.FULL);

    for (final Block block : importer.getBlocks()) {
        final ProtocolSpec protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
        final BlockImporter blockImporter = protocolSpec.getBlockImporter();
        //blockImporter.importBlock(ether, block, HeaderValidationMode.LIGHT);
        blockImporter.fastImportBlock(ether, block,gen.receipts(block),
                HeaderValidationMode.LIGHT_SKIP_DETACHED,HeaderValidationMode.LIGHT_SKIP_DETACHED);
      latestFastSyncdBlockNumber = block.getHeader().getNumber();
    }


    int pivotBlockIndex = importer.getBlocks().size() / 3;
    //TODO: the way we are establishing state isn't exactly analogous to what is happening in a fastsync.

    for(int i = 0; i< pivotBlockIndex*2; i++) {  //Then do a third of 'em FULL,
      Block toReimportFully = importer.getBlocks().get(i);
      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(
              toReimportFully.getHeader().getNumber());
      final BlockImporter blockImporter = protocolSpec.getBlockImporter();
      blockImporter.importBlock(ether, toReimportFully, HeaderValidationMode.FULL);
      latestFullySyncdBlockNumber = toReimportFully.getHeader().getNumber();
    }

    final JsonRpcTestMethodsFactory factory =
        new JsonRpcTestMethodsFactory(importer, blockchain, state, ether);

    ethGetBlockNumber = factory.methods().get("eth_getBlockByNumber");
  }

  @Test
  public void shouldReturnedLatestFullSynced() {

    assertThat(latestFullySyncdBlockNumber.longValue()).isNotEqualTo(0L);
    assertThat(latestFastSyncdBlockNumber.longValue()).isNotEqualTo(latestFullySyncdBlockNumber);
    //otherwise our setup didn't work as expected
    Object[] params = {"latest", false};
    JsonRpcRequestContext ctx = new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_getBlockByNumber", params));
    Assertions.assertThatNoException().isThrownBy(() -> {
      final JsonRpcResponse resp = ethGetBlockNumber.response(ctx);
      assertThat(resp).isNotNull();
      assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
      Object r = ((JsonRpcSuccessResponse)resp).getResult();
      assertThat(r).isInstanceOf(BlockResult.class);
      BlockResult br = (BlockResult)r;
      assertThat(br.getNumber()).isEqualTo("0x"+Long.toHexString(latestFullySyncdBlockNumber));
    });

    //TODO: what test to cover including transactions?

  }
}
