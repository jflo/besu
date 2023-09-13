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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.apache.tuweni.bytes.Bytes32;
import org.checkerframework.checker.signedness.qual.Unsigned;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.AbstractEngineNewPayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineCallListener;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.EngineExecutionPayloadParameterV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;

public abstract class ExecutionEngineJsonRpcMethod implements JsonRpcMethod {
  // engine api calls are synchronous, no need for volatile
  protected long lastInvalidWarn = 0;

  public enum EngineStatus {
    VALID,
    INVALID,
    SYNCING,
    ACCEPTED,
    INVALID_BLOCK_HASH;
  }

  public static final long ENGINE_API_LOGGING_THRESHOLD = 60000L;
  protected final Vertx syncVertx;
  private static final Logger LOG = LoggerFactory.getLogger(ExecutionEngineJsonRpcMethod.class);
  protected final Optional<MergeContext> mergeContextOptional;
  protected final Supplier<MergeContext> mergeContext;
  protected final Optional<ProtocolSchedule> protocolSchedule;
  protected final ProtocolContext protocolContext;
  protected final EngineCallListener engineCallListener;

  protected ExecutionEngineJsonRpcMethod(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener) {
    this.syncVertx = vertx;
    this.protocolSchedule = Optional.of(protocolSchedule);
    this.protocolContext = protocolContext;
    this.mergeContextOptional = protocolContext.safeConsensusContext(MergeContext.class);
    this.mergeContext = mergeContextOptional::orElseThrow;
    this.engineCallListener = engineCallListener;
  }

  protected ExecutionEngineJsonRpcMethod(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener) {
    this.syncVertx = vertx;
    this.protocolSchedule = Optional.empty();
    this.protocolContext = protocolContext;
    this.mergeContextOptional = protocolContext.safeConsensusContext(MergeContext.class);
    this.mergeContext = mergeContextOptional::orElseThrow;
    this.engineCallListener = engineCallListener;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {

    final CompletableFuture<JsonRpcResponse> cf = new CompletableFuture<>();

    syncVertx.<JsonRpcResponse>executeBlocking(
        z -> {
          LOG.trace(
              "execution engine JSON-RPC request {} {}",
              this.getName(),
              request.getRequest().getParams());
          z.tryComplete(syncResponse(request));
        },
        true,
        resp ->
            cf.complete(
                resp.otherwise(
                        t -> {
                          if (LOG.isDebugEnabled()) {
                            LOG.atDebug()
                                .setMessage("failed to exec consensus method {}")
                                .addArgument(this.getName())
                                .setCause(t)
                                .log();
                          } else {
                            LOG.atError()
                                .setMessage("failed to exec consensus method {}, error: {}")
                                .addArgument(this.getName())
                                .addArgument(t.getMessage())
                                .log();
                          }
                          return new JsonRpcErrorResponse(
                              request.getRequest().getId(), RpcErrorType.INVALID_REQUEST);
                        })
                    .result()));
    try {
      return cf.get();
    } catch (InterruptedException e) {
      LOG.error("Failed to get execution engine response", e);
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.TIMEOUT_ERROR);
    } catch (ExecutionException e) {
      LOG.error("Failed to get execution engine response", e);
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INTERNAL_ERROR);
    }
  }

  public abstract JsonRpcResponse syncResponse(final JsonRpcRequestContext request);

  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    return ValidationResult.valid();
  }

  public record BlockIdentifier(@Unsigned long number, Bytes32 blockHash, Bytes32 parentHash) {}

  protected JsonRpcResponse respondWithInvalid(
          final Object requestId,
          final BlockIdentifier param,
          final Hash latestValidHash,
          final EngineStatus invalidStatus,
          final String validationError) {
    if (!INVALID.equals(invalidStatus) && !INVALID_BLOCK_HASH.equals(invalidStatus)) {
      throw new IllegalArgumentException(
              "Don't call respondWithInvalid() with non-invalid status of " + invalidStatus.toString());
    }
    final String invalidBlockLogMessage =
            String.format(
                    "Invalid new payload: number: %s, hash: %s, parentHash: %s, latestValidHash: %s, status: %s, validationError: %s",
                    Long.toUnsignedString(param.number()),
                    param.blockHash(),
                    param.parentHash(),
                    latestValidHash == null ? null : latestValidHash.toHexString(),
                    invalidStatus.name(),
                    validationError);
    // always log invalid at DEBUG
    LOG.debug(invalidBlockLogMessage);
    // periodically log at WARN
    if (lastInvalidWarn + ENGINE_API_LOGGING_THRESHOLD < System.currentTimeMillis()) {
      lastInvalidWarn = System.currentTimeMillis();
      LOG.warn(invalidBlockLogMessage);
    }
    return new JsonRpcSuccessResponse(
            requestId,
            new EnginePayloadStatusResult(
                    invalidStatus, latestValidHash, Optional.of(validationError)));
  }

}
