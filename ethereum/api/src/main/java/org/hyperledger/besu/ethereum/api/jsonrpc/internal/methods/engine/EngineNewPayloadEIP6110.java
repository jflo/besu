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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.DepositsValidatorProvider.getDepositsValidator;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterEIP6110;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.checkerframework.checker.signedness.qual.Unsigned;

public class EngineNewPayloadEIP6110 extends EngineNewPayloadV3 {

  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;

  public EngineNewPayloadEIP6110(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    this.cancun = timestampSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateRequest(
      final JsonRpcRequestContext requestContext) {
    NewPayloadParameterEIP6110 newPayloadParam =
        requestContext.getRequiredParameter(0, NewPayloadParameterEIP6110.class);
    final Optional<List<Deposit>> maybeDeposits =
        Optional.ofNullable(newPayloadParam.getDeposits())
            .map(ds -> ds.stream().map(DepositParameter::toDeposit).collect(toList()));

    ValidationResult<RpcErrorType> v3Validity = super.validateRequest(requestContext);
    if (!v3Validity.isValid()) {
      return v3Validity;
    } else if (!getDepositsValidator(
            protocolSchedule.get(),
            newPayloadParam.getTimestamp(),
            newPayloadParam.getBlockNumber())
        .validateDepositParameter(maybeDeposits)) {
      return ValidationResult.invalid(INVALID_PARAMS, "Invalid deposits");
    } else {
      return ValidationResult.valid();
    }
  }

  @Override
  @SuppressWarnings("signedness:override.param")
  protected ValidationResult<RpcErrorType> validateForkSupported(
      final @Unsigned long blockTimestamp) {
    if (protocolSchedule.isPresent()) {
      if (cancun.isPresent()
          && Long.compareUnsigned(blockTimestamp, cancun.get().milestone()) >= 0) {
        return ValidationResult.valid();
      } else {
        return ValidationResult.invalid(
            RpcErrorType.UNSUPPORTED_FORK,
            "Cancun configured to start at timestamp: "
                + Long.toUnsignedString(cancun.get().milestone()));
      }
    } else {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK, "Configuration error, no schedule for Cancun fork set");
    }
  }

  @Override
  protected BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext context, final Hash txRoot) {
    final NewPayloadParameterEIP6110 blockParam =
        context.getRequiredParameter(0, NewPayloadParameterEIP6110.class);

    final Optional<List<Deposit>> maybeDeposits =
        Optional.ofNullable(blockParam.getDeposits())
            .map(ds -> ds.stream().map(DepositParameter::toDeposit).collect(toList()));
    final BlockHeaderBuilder builder = super.composeNewHeader(context, txRoot);
    builder.depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null));
    return builder;
  }
}
