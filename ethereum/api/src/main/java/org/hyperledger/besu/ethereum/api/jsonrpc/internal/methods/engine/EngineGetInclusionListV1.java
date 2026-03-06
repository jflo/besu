/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.DefaultInclusionListSelector;
import org.hyperledger.besu.ethereum.core.InclusionListConstants;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetInclusionListV1 extends ExecutionEngineJsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGetInclusionListV1.class);

  private final TransactionPool transactionPool;

  public EngineGetInclusionListV1(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener,
      final TransactionPool transactionPool) {
    super(vertx, protocolContext, engineCallListener);
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_INCLUSION_LIST_V1.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
    engineCallListener.executionEngineCalled();

    final Hash parentHash;
    try {
      parentHash = request.getRequiredParameter(0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid parent hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_HASH_PARAMS, e);
    }

    final Optional<BlockHeader> parentHeader =
        protocolContext.getBlockchain().getBlockHeader(parentHash);
    if (parentHeader.isEmpty()) {
      LOG.debug("Unknown parent block hash: {}", parentHash);
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.UNKNOWN_PARENT);
    }

    final Optional<Wei> baseFeePerGas = parentHeader.get().getBaseFee();
    final DefaultInclusionListSelector selector = new DefaultInclusionListSelector(baseFeePerGas);

    final List<Transaction> mempoolTransactions =
        transactionPool.getPendingTransactions().stream()
            .map(PendingTransaction::getTransaction)
            .toList();

    final List<Bytes> selectedTransactions =
        selector.selectTransactions(
            parentHash, mempoolTransactions, InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST);

    final List<String> result = selectedTransactions.stream().map(Bytes::toHexString).toList();

    LOG.debug(
        "engine_getInclusionListV1: parentHash={}, selected {} transactions",
        parentHash,
        result.size());

    return new JsonRpcSuccessResponse(request.getRequest().getId(), result);
  }
}
