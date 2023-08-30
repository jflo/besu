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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.io.Resources;
import com.google.gson.JsonObject;
import io.vertx.core.json.Json;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetTransactionByHash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.BlobTransactionDecoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.condition.txpool.TxPoolConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthSendRawTransactionTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class EIP4844AcceptanceTest extends AcceptanceTestBase {


  private Node originatingNode;
  private Node peeringNode;

  @BeforeEach
  public void setUp() throws Exception {
    originatingNode = besu.createMinerNode("originatingNode", configureNode(true));
    peeringNode = besu.createArchiveNode("peeringNode", configureNode(false));

    cluster.start(peeringNode,originatingNode);
  }

  @Test
  public void shouldServeBlobTransactionToPeer() throws URISyntaxException, IOException, InterruptedException {
    //send the raw tx message from hive.
    URI exampleMessage = EIP4844AcceptanceTest.class.getResource("/jsonrpc/RawBlobTx.json").toURI();
    String rawJson = Resources.toString(exampleMessage.toURL(), Charset.defaultCharset());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(rawJson);
    JsonNode rlpNode = root.path("params");
    String rlpHex = rlpNode.get(0).asText();
    EthSendRawTransactionTransaction toSend = new EthSendRawTransactionTransaction(rlpHex);
    Hash blobTxHash = originatingNode.execute(toSend);
    //wait, but how long?
    Thread.sleep(500);
    //assert that the tx is included on the originating node, contains blob details

    //assert that originating node broadcasts the tx hash to the peering node
    //assert that the peering node retreives full tx from originating node, contains blob details
    //assert that the peering node includes the tx in its tx pool, contains blob details

    cluster.verify(txPoolConditions.inTransactionPool(Hash.fromHexString(hash)));
    originatingNode.verify(txPoolConditions.inTransactionPool(Hash.fromHexString(hash)));


  }

  private UnaryOperator<BesuNodeConfigurationBuilder> configureNode(final boolean bootNodeEligible) {
    final String genesisFile = GenesisConfigurationFactory.readGenesisFile("/dev/EIP4844Devnet8.json");
    return b ->
            b.genesisConfigProvider((a) -> Optional.of(genesisFile))
                    .bootnodeEligible(bootNodeEligible)
                    .engineRpcEnabled(true)
                    .devMode(false);
  }


}
