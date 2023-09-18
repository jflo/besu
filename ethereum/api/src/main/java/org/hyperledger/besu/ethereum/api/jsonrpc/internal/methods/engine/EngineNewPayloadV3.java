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

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV3;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.checkerframework.checker.signedness.qual.Unsigned;

public class EngineNewPayloadV3 extends EngineNewPayloadV2 {

  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;

  public EngineNewPayloadV3(
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
  @SuppressWarnings("unchecked")
  protected <P extends NewPayloadParameterV1> P parseVersionedParam(final JsonRpcRequestContext request) {
    return (P) request.getRequiredParameter(0, NewPayloadParameterV3.class);
  }
  @Override
  protected ValidationResult<RpcErrorType> validateRequest(
      final JsonRpcRequestContext requestContext) {
    NewPayloadParameterV3 newPayloadParam =
        requestContext.getRequiredParameter(0, NewPayloadParameterV3.class);

    final Optional<List<String>> maybeVersionedHashParam =
        requestContext.getOptionalList(1, String.class);
    Optional<String> maybeParentBeaconBlockRootParam =
        requestContext.getOptionalParameter(2, String.class);
    /*
    final Optional<Bytes32> maybeParentBeaconBlockRoot =
            maybeParentBeaconBlockRootParam.map(Bytes32::fromHexString);
    */
    ValidationResult<RpcErrorType> v2Validity = super.validateRequest(requestContext);
    if (!v2Validity.isValid()) {
      return v2Validity;
    } else if (newPayloadParam.getBlobGasUsed() == null
        || newPayloadParam.getExcessBlobGas() == null) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing blob gas fields");
    } else if (maybeVersionedHashParam == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS, "Missing versioned hashes field");
    } else if (maybeParentBeaconBlockRootParam.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS, "Missing parent beacon block root field");
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
  protected ValidationResult<RpcErrorType> validateBlobs(
      final Block newBlock,
      final Optional<BlockHeader> maybeParentHeader,
      final JsonRpcRequestContext requestContext,
      final ProtocolSpec protocolSpec) {

    final Optional<List<String>> maybeVersionedHashParam =
        requestContext.getOptionalList(1, String.class);
    final Optional<List<VersionedHash>> maybeVersionedHashes;
    try {
      maybeVersionedHashes = extractVersionedHashes(maybeVersionedHashParam);
    } catch (RuntimeException ex) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Invalid versionedHash");
    }

    var blobTransactions =
        newBlock.getBody().getTransactions().stream()
            .filter(transaction -> transaction.getType().supportsBlob())
            .toList();

    final List<VersionedHash> transactionVersionedHashes = new ArrayList<>();
    for (Transaction transaction : blobTransactions) {
      var versionedHashes = transaction.getVersionedHashes();
      // blob transactions must have at least one blob
      if (versionedHashes.isEmpty()) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS, "There must be at least one blob");
      }
      transactionVersionedHashes.addAll(versionedHashes.get());
    }

    if (maybeVersionedHashes.isEmpty() && !transactionVersionedHashes.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS, "Payload must contain versioned hashes for transactions");
    }

    // Validate versionedHashesParam
    if (maybeVersionedHashes.isPresent()
        && !maybeVersionedHashes.get().equals(transactionVersionedHashes)) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS,
          "Versioned hashes from blob transactions do not match expected values");
    }

    // Validate excessBlobGas
    if (maybeParentHeader.isPresent()) {
      if (!validateExcessBlobGas(newBlock.getHeader(), maybeParentHeader.get(), protocolSpec)) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS,
            "Payload excessBlobGas does not match calculated excessBlobGas");
      }
    }

    // Validate blobGasUsed
    if (newBlock.getHeader().getBlobGasUsed().isPresent() && maybeVersionedHashes.isPresent()) {
      if (!validateBlobGasUsed(newBlock.getHeader(), maybeVersionedHashes.get(), protocolSpec)) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS,
            "Payload BlobGasUsed does not match calculated BlobGasUsed");
      }
    }
    return ValidationResult.valid();
  }

  private boolean validateExcessBlobGas(
      final BlockHeader header, final BlockHeader parentHeader, final ProtocolSpec protocolSpec) {
    BlobGas calculatedBlobGas =
        ExcessBlobGasCalculator.calculateExcessBlobGasForParent(protocolSpec, parentHeader);
    return header.getExcessBlobGas().orElse(BlobGas.ZERO).equals(calculatedBlobGas);
  }

  private boolean validateBlobGasUsed(
      final BlockHeader header,
      final List<VersionedHash> maybeVersionedHashes,
      final ProtocolSpec protocolSpec) {
    var calculatedBlobGas =
        protocolSpec.getGasCalculator().blobGasCost(maybeVersionedHashes.size());
    return header.getBlobGasUsed().orElse(0L).equals(calculatedBlobGas);
  }

  private Optional<List<VersionedHash>> extractVersionedHashes(
      final Optional<List<String>> maybeVersionedHashParam) {
    return maybeVersionedHashParam.map(
        versionedHashes ->
            versionedHashes.stream()
                .map(Bytes32::fromHexString)
                .map(
                    hash -> {
                      try {
                        return new VersionedHash(hash);
                      } catch (InvalidParameterException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .collect(Collectors.toList()));
  }

  @Override
  @SuppressWarnings("signedness:argument")
  protected BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext context, final Hash txRoot) {
    final NewPayloadParameterV3 blockParam =
        context.getRequiredParameter(0, NewPayloadParameterV3.class);
    String parentBeaconBlockRootParam = context.getRequiredParameter(2, String.class);

    final BlockHeaderBuilder builder = super.composeNewHeader(context, txRoot);
    builder
        .blobGasUsed(blockParam.getBlobGasUsed())
        .excessBlobGas(BlobGas.fromHexString(blockParam.getExcessBlobGas()))
        .parentBeaconBlockRoot(Bytes32.fromHexString(parentBeaconBlockRootParam));
    return builder;
  }
}
