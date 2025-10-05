/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.execution;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous JSON-RPC processor that executes methods on Vert.x worker threads with configurable
 * timeout. This processor wraps another processor and delegates execution to it in a non-blocking
 * manner.
 */
public class AsyncJsonRpcProcessor implements JsonRpcProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncJsonRpcProcessor.class);
  private static final long DEFAULT_TIMEOUT_MILLIS = 2000; // 2 seconds

  private final JsonRpcProcessor delegate;
  private final Vertx vertx;
  private final long timeoutMillis;

  /**
   * Creates an async processor with default 2-second timeout.
   *
   * @param delegate the processor to delegate execution to
   * @param vertx the Vert.x instance for async execution
   */
  public AsyncJsonRpcProcessor(final JsonRpcProcessor delegate, final Vertx vertx) {
    this(delegate, vertx, DEFAULT_TIMEOUT_MILLIS);
  }

  /**
   * Creates an async processor with custom timeout.
   *
   * @param delegate the processor to delegate execution to
   * @param vertx the Vert.x instance for async execution
   * @param timeoutMillis the timeout in milliseconds
   */
  public AsyncJsonRpcProcessor(
      final JsonRpcProcessor delegate, final Vertx vertx, final long timeoutMillis) {
    this.delegate = delegate;
    this.vertx = vertx;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public JsonRpcResponse process(
      final JsonRpcRequestId id,
      final JsonRpcMethod method,
      final Span metricSpan,
      final JsonRpcRequestContext request) {

    final Promise<JsonRpcResponse> promise = Promise.promise();
    final CompletableFuture<JsonRpcResponse> resultFuture = new CompletableFuture<>();

    // Execute on worker thread pool to avoid blocking event loop
    vertx.<JsonRpcResponse>executeBlocking(
        blockingPromise -> {
          try {
            // Check if already timed out before starting
            if (resultFuture.isDone()) {
              return;
            }

            final JsonRpcResponse response = delegate.process(id, method, metricSpan, request);
            blockingPromise.complete(response);
          } catch (final Exception e) {
            // Only propagate if not already timed out
            if (!resultFuture.isDone()) {
              blockingPromise.fail(e);
            }
          }
        },
        false, // ordered = false for better concurrency
        ar -> {
          if (ar.succeeded()) {
            resultFuture.complete(ar.result());
            promise.complete(ar.result());
          } else {
            resultFuture.completeExceptionally(ar.cause());
            promise.fail(ar.cause());
          }
        });

    // Wait for result with timeout
    try {
      return resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      LOG.warn(
          "JSON-RPC method {} timed out after {} ms for request {}",
          method.getName(),
          timeoutMillis,
          id);
      return new JsonRpcErrorResponse(id, RpcErrorType.TIMEOUT_ERROR);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("JSON-RPC method {} was interrupted for request {}", method.getName(), id, e);
      return new JsonRpcErrorResponse(id, RpcErrorType.INTERNAL_ERROR);
    } catch (final Exception e) {
      LOG.error("Error executing JSON-RPC method {} for request {}", method.getName(), id, e);
      return new JsonRpcErrorResponse(id, RpcErrorType.INTERNAL_ERROR);
    }
  }
}
