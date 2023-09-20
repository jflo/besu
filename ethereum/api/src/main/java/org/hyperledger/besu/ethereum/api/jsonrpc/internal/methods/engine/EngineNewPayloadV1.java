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

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidationResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV1;
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
import org.hyperledger.besu.ethereum.rlp.RLPException;

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
  protected <P extends NewPayloadParameterV1> P parseVersionedParam(final JsonRpcRequestContext request) {
    NewPayloadParameterV1 param =  request.getRequiredParameter(0, NewPayloadParameterV1.class);
    return (P) param;
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
  protected <P extends NewPayloadParameterV1> ValidationResult<RpcErrorType> validateRequest(final P newPayloadParam, final JsonRpcRequestContext requestContext) {
    return ValidationResult.valid();
  }

  @Override
  protected <P extends NewPayloadParameterV1> EngineBlockValidationResult validateBlock( final P payload) {
    if(payload.getExtraData() == null) {
      return new EngineBlockValidationResult(EngineStatus.INVALID, new BlockValidationResult("extraData is null"));
    } else if(Bytes32.fromHexString(payload.getExtraData()).size() > 32) {
      return new EngineBlockValidationResult(EngineStatus.INVALID, new BlockValidationResult("extraData is too big: "+payload.getExtraData()));
    }
    return new EngineBlockValidationResult(EngineStatus.VALID, new BlockValidationResult());
  }

  @Override
  @SuppressWarnings("signedness:argument")
  protected <P extends NewPayloadParameterV1> BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext requestContext, final P newPayloadParam, final Hash txRoot) {

    final BlockHeaderBuilder builder = new BlockHeaderBuilder();
    builder
        .parentHash(newPayloadParam.getParentHash())
        .ommersHash(OMMERS_HASH_CONSTANT)
        .coinbase(newPayloadParam.getFeeRecipient())
        .stateRoot(newPayloadParam.getStateRoot())
        .transactionsRoot(txRoot)
        .receiptsRoot(newPayloadParam.getReceiptsRoot())
        .logsBloom(newPayloadParam.getLogsBloom())
        .difficulty(Difficulty.ZERO)
        .number(newPayloadParam.getBlockNumber())
        .gasLimit(newPayloadParam.getGasLimit())
        .gasUsed(newPayloadParam.getGasUsed())
        .timestamp(newPayloadParam.getTimestamp())
        .extraData(Bytes.fromHexString(newPayloadParam.getExtraData()))
        .baseFee(newPayloadParam.getBaseFeePerGas())
        .prevRandao(newPayloadParam.getPrevRandao())
        .nonce(0)
        .blockHeaderFunctions(headerFunctions);
    return builder;
  }

  @Override
  protected BlockBodyBuilder composeBody(final JsonRpcRequestContext requestContext) throws RLPException {
    NewPayloadParameterV1 blockParam =
        requestContext.getRequiredParameter(0, NewPayloadParameterV1.class);
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

}
