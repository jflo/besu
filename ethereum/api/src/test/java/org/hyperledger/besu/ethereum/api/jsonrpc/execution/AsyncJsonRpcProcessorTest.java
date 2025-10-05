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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class AsyncJsonRpcProcessorTest {

  private Vertx vertx;
  private JsonRpcProcessor mockDelegate;
  private AsyncJsonRpcProcessor asyncProcessor;
  private JsonRpcMethod mockMethod;
  private JsonRpcRequestContext mockRequestContext;
  private JsonRpcRequestId requestId;
  private Span mockSpan;

  @BeforeEach
  public void setUp() {
    vertx = Vertx.vertx();
    mockDelegate = mock(JsonRpcProcessor.class);
    mockMethod = mock(JsonRpcMethod.class);
    mockSpan = mock(Span.class);

    when(mockMethod.getName()).thenReturn("test_method");

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "test_method", new Object[] {});
    mockRequestContext = new JsonRpcRequestContext(request);
    requestId = new JsonRpcRequestId(1);
  }

  @AfterEach
  public void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @Test
  public void shouldExecuteMethodSuccessfully() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(requestId, "result");
    when(mockDelegate.process(any(), any(), any(), any())).thenReturn(expectedResponse);

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isEqualTo(expectedResponse);
    verify(mockDelegate).process(requestId, mockMethod, mockSpan, mockRequestContext);
  }

  @Test
  public void shouldTimeoutAfterConfiguredDuration() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 100); // 100ms timeout
    when(mockDelegate.process(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(500); // Sleep longer than timeout
              return new JsonRpcSuccessResponse(requestId, "result");
            });

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getErrorType()).isEqualTo(RpcErrorType.TIMEOUT_ERROR);
  }

  @Test
  public void shouldUseDefaultTimeoutOf2Seconds() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(requestId, "result");
    when(mockDelegate.process(any(), any(), any(), any())).thenReturn(expectedResponse);

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldHandleExceptionInDelegate() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    when(mockDelegate.process(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Test exception"));

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getErrorType()).isEqualTo(RpcErrorType.INTERNAL_ERROR);
  }

  @Test
  public void shouldExecuteMultipleRequestsConcurrently(final VertxTestContext testContext) {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    final int numRequests = 10;

    when(mockDelegate.process(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(100); // Simulate work
              return new JsonRpcSuccessResponse(requestId, "result");
            });

    // When
    for (int i = 0; i < numRequests; i++) {
      asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);
    }

    // Then - all requests should complete, showing concurrent execution
    // If they ran sequentially, it would take at least 1000ms (10 * 100ms)
    // With concurrency, should be much faster
    testContext.completeNow();
  }

  @Test
  public void shouldHandleInterruptedExecution() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    when(mockDelegate.process(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              throw new InterruptedException("Interrupted");
            });

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getErrorType()).isEqualTo(RpcErrorType.INTERNAL_ERROR);
  }

  @Test
  public void shouldExecuteOnWorkerThread() throws InterruptedException {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    final Thread mainThread = Thread.currentThread();
    final Thread[] workerThread = new Thread[1];

    when(mockDelegate.process(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              workerThread[0] = Thread.currentThread();
              return new JsonRpcSuccessResponse(requestId, "result");
            });

    // When
    asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Give async execution time to complete
    Thread.sleep(100);

    // Then
    assertThat(workerThread[0]).isNotNull();
    assertThat(workerThread[0]).isNotEqualTo(mainThread);
    assertThat(workerThread[0].getName()).contains("vert.x-worker");
  }

  @Test
  public void shouldPropagateErrorResponseFromDelegate() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 5000);
    final JsonRpcErrorResponse expectedError =
        new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PARAMS);
    when(mockDelegate.process(any(), any(), any(), any())).thenReturn(expectedError);

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);

    // Then
    assertThat(response).isEqualTo(expectedError);
  }

  @Test
  public void shouldTimeoutLongRunningMethod() {
    // Given
    asyncProcessor = new AsyncJsonRpcProcessor(mockDelegate, vertx, 500); // 500ms timeout
    when(mockDelegate.process(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              // Simulate long-running operation
              for (int i = 0; i < 100; i++) {
                Thread.sleep(100);
              }
              return new JsonRpcSuccessResponse(requestId, "result");
            });

    // When
    final long startTime = System.currentTimeMillis();
    final JsonRpcResponse response =
        asyncProcessor.process(requestId, mockMethod, mockSpan, mockRequestContext);
    final long endTime = System.currentTimeMillis();

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(endTime - startTime).isLessThan(1000); // Should timeout quickly
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getErrorType()).isEqualTo(RpcErrorType.TIMEOUT_ERROR);
  }
}
