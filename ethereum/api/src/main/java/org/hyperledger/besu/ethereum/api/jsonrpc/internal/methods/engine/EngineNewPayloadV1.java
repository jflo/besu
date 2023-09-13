/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.EngineExecutionPayloadParameterV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBodyBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;

public class EngineNewPayloadV1 extends AbstractEngineNewPayload {

  public EngineNewPayloadV1(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <P extends EngineExecutionPayloadParameterV1> P parseVersionedParam(final JsonRpcRequestContext request) {
    return (P) request.getRequiredParameter(0, EngineExecutionPayloadParameterV1.class);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V1.getMethodName();
  }

  @Override
  protected EngineStatus getInvalidBlockHashStatus() {
    return INVALID_BLOCK_HASH;
  }

  @Override
  protected ValidationResult<RpcErrorType> validateRequest(
      final JsonRpcRequestContext requestContext) {
    EngineExecutionPayloadParameterV1 blockParam =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameterV1.class);
    if(blockParam.getExtraData() == null) {
      return ValidationResult.invalid(INVALID_PARAMS, "missing extraData");
    }
    return ValidationResult.valid();
  }

  @Override
  @SuppressWarnings("signedness:argument")
  protected BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext requestContext, final Hash txRoot) {
    EngineExecutionPayloadParameterV1 blockParam =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameterV1.class);
    final BlockHeaderBuilder builder = new BlockHeaderBuilder();
    builder
        .parentHash(blockParam.getParentHash())
        .ommersHash(OMMERS_HASH_CONSTANT)
        .coinbase(blockParam.getFeeRecipient())
        .stateRoot(blockParam.getStateRoot())
        .transactionsRoot(txRoot)
        .receiptsRoot(blockParam.getReceiptsRoot())
        .logsBloom(blockParam.getLogsBloom())
        .difficulty(Difficulty.ZERO)
        .number(blockParam.getBlockNumber())
        .gasLimit(blockParam.getGasLimit())
        .gasUsed(blockParam.getGasUsed())
        .timestamp(blockParam.getTimestamp())
        .extraData(Bytes.fromHexString(blockParam.getExtraData()))
        .baseFee(blockParam.getBaseFeePerGas())
        .prevRandao(blockParam.getPrevRandao())
        .nonce(0)
        .blockHeaderFunctions(headerFunctions);
    return builder;
  }

  @Override
  protected BlockBodyBuilder composeBody(final JsonRpcRequestContext requestContext) {
    EngineExecutionPayloadParameterV1 blockParam =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameterV1.class);
    final List<Transaction> transactions;

    transactions =
        blockParam.getTransactions().stream()
            .map(Bytes::fromHexString)
            .map(TransactionDecoder::decodeOpaqueBytes)
            .collect(Collectors.toList());
    BlockBodyBuilder builder = new BlockBodyBuilder();
    builder.transactions(transactions);
    return builder;
  }

  @Override
  protected ValidationResult<RpcErrorType> validateBlobs(
      final Block newBlock,
      final Optional<BlockHeader> maybeParentHeader,
      final JsonRpcRequestContext requestContext,
      final ProtocolSpec protocolSpec) {
    return ValidationResult.valid();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
    throw new IllegalStateException("caller should have been overridden");
  }
}
