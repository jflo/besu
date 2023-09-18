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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_REQUEST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV2;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  @SuppressWarnings("unchecked")
  protected <P extends NewPayloadParameterV1> P parseVersionedParam(final JsonRpcRequestContext request) {
    return (P) request.getRequiredParameter(0, NewPayloadParameterV2.class);
  }

  @Override
  protected ValidationResult<RpcErrorType> validateRequest(
      final JsonRpcRequestContext requestContext) {

    final NewPayloadParameterV2 newPayloadParam =
        requestContext.getRequiredParameter(0, NewPayloadParameterV2.class);
    WithdrawalsValidator validator = getWithdrawalsValidator(
            protocolSchedule.get(), newPayloadParam.getTimestamp(), newPayloadParam.getBlockNumber());
    Optional<List<Withdrawal>> maybeWithdrawals = Optional.empty();
    String errorMessage = "Invalid withdrawals";
    if(validator instanceof WithdrawalsValidator.AllowedWithdrawals) {
       maybeWithdrawals =
              Optional.ofNullable(newPayloadParam.getWithdrawals())
                      .map(ws -> ws.stream().map(WithdrawalParameter::toWithdrawal).collect(toList()));


    } else {
      if(newPayloadParam.getWithdrawals() != null) {
        errorMessage = errorMessage + ", shanghai fork not enabled yet, payload had withdrawals field";
        return ValidationResult.invalid(INVALID_PARAMS, errorMessage);
      }

    }

    if (!validator.validateWithdrawals(maybeWithdrawals)) {
      return ValidationResult.invalid(INVALID_PARAMS, errorMessage);
    } else {
      return ValidationResult.valid();
    }

  }

  @Override
  protected BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext requestContext, final Hash txRoot) {
    BlockHeaderBuilder builder = super.composeNewHeader(requestContext, txRoot);
    NewPayloadParameterV2 blockParam =
        requestContext.getRequiredParameter(0, NewPayloadParameterV2.class);
    final Optional<List<Withdrawal>> maybeWithdrawals =
        Optional.ofNullable(blockParam.getWithdrawals())
            .map(ws -> ws.stream().map(WithdrawalParameter::toWithdrawal).collect(toList()));

    builder.withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(BodyValidation.withdrawalsRoot(Collections.emptyList())));
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
