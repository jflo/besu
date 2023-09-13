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

import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.WithdrawalsValidatorProvider.getWithdrawalsValidator;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.EngineExecutionPayloadParameterV2;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;

public class EngineNewPayloadV2 extends EngineNewPayloadV1 {

  public EngineNewPayloadV2(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
  }

  @Override
  protected ValidationResult<RpcErrorType> validateRequest(
      final JsonRpcRequestContext requestContext) {

    EngineExecutionPayloadParameterV2 blockParam =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameterV2.class);
    final Optional<List<Withdrawal>> maybeWithdrawals =
        Optional.ofNullable(blockParam.getWithdrawals())
            .map(ws -> ws.stream().map(WithdrawalParameter::toWithdrawal).collect(toList()));

    if (!getWithdrawalsValidator(
            protocolSchedule.get(), blockParam.getTimestamp(), blockParam.getBlockNumber())
        .validateWithdrawals(maybeWithdrawals)) {
      return ValidationResult.invalid(INVALID_PARAMS, "Invalid withdrawals");
    } else {
      return ValidationResult.valid();
    }
  }

  @Override
  protected BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext requestContext, final Hash txRoot) {
    BlockHeaderBuilder builder = super.composeNewHeader(requestContext, txRoot);
    EngineExecutionPayloadParameterV2 blockParam =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameterV2.class);
    final Optional<List<Withdrawal>> maybeWithdrawals =
        Optional.ofNullable(blockParam.getWithdrawals())
            .map(ws -> ws.stream().map(WithdrawalParameter::toWithdrawal).collect(toList()));

    builder.withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null));
    return builder;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V2.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateBlobs(
      final Block newPayload,
      final Optional<BlockHeader> maybeParentHeader,
      final JsonRpcRequestContext context,
      final ProtocolSpec protocolSpec) {
    return ValidationResult.valid();
  }
}
