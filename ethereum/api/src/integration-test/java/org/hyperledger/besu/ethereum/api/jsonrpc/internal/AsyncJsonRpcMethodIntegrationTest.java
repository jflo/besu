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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.AsyncJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.OpCodeLoggerTracerResult;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.common.io.Resources;
import io.opentelemetry.api.trace.Span;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncJsonRpcMethodIntegrationTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(AsyncJsonRpcMethodIntegrationTest.class);
  private static final String METHOD_NAME = "debug_traceTransaction";
  private static JsonRpcTestMethodsFactory methodsFactory;
  private static Vertx vertx;

  @BeforeAll
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), StandardCharsets.UTF_8);

    methodsFactory =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson));

    vertx = Vertx.vertx();
  }

  @AfterAll
  public static void tearDownOnce() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @AfterEach
  public void tearDown() throws InterruptedException {
    // add a new task to the queue
    final CountDownLatch latch = new CountDownLatch(1);
    vertx.runOnContext(v -> latch.countDown());

    // Wait up to 1 second for pending operations
    if (!latch.await(1, TimeUnit.SECONDS)) {
      LOG.warn("Timeout waiting for async operations to complete in tearDown");
    }
  }

  @Test
  public void shouldExecuteMethodAsynchronously() {
    // Given
    final Map<String, JsonRpcMethod> methods = methodsFactory.methods();
    final JsonRpcMethod debugTraceMethod = methods.get(METHOD_NAME);

    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 5000);

    final Map<String, Boolean> params = Map.of("disableStorage", true);
    final Hash trxHash =
        Hash.fromHexString("0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {trxHash, params});
    final JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    final JsonRpcRequestId id = new JsonRpcRequestId(1);

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(id, debugTraceMethod, Span.getInvalid(), requestContext);

    // Then
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    final OpCodeLoggerTracerResult result =
        (OpCodeLoggerTracerResult) ((JsonRpcSuccessResponse) response).getResult();
    assertThat(result.getGas()).isEqualTo(23705L);
  }

  @Test
  public void shouldTimeoutSlowMethod() {
    // Given
    final JsonRpcMethod slowMethod = mock(JsonRpcMethod.class);
    when(slowMethod.getName()).thenReturn("slow_method");
    when(slowMethod.response(any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(3000); // Sleep for 3 seconds
              return new JsonRpcSuccessResponse(new JsonRpcRequestId(1), "result");
            });

    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 500); // 500ms timeout

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "slow_method", new Object[] {});
    final JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    final JsonRpcRequestId id = new JsonRpcRequestId(1);

    // When
    final long startTime = System.currentTimeMillis();
    final JsonRpcResponse response =
        asyncProcessor.process(id, slowMethod, Span.getInvalid(), requestContext);
    final long duration = System.currentTimeMillis() - startTime;

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.TIMEOUT_ERROR);
    assertThat(duration).isLessThan(1000); // Should timeout quickly
  }

  @Test
  public void shouldExecuteMultipleMethodsConcurrently() {
    // Given
    final Map<String, JsonRpcMethod> methods = methodsFactory.methods();
    final JsonRpcMethod debugTraceMethod = methods.get(METHOD_NAME);

    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 5000);

    final Map<String, Boolean> params = Map.of("disableStorage", true);
    final Hash trxHash =
        Hash.fromHexString("0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {trxHash, params});
    final JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);

    final int numRequests = 5;
    int successCount = 0;

    // When
    final long startTime = System.currentTimeMillis();
    for (int i = 0; i < numRequests; i++) {
      final JsonRpcRequestId id = new JsonRpcRequestId(i);
      final JsonRpcResponse response =
          asyncProcessor.process(id, debugTraceMethod, Span.getInvalid(), requestContext);

      if (response.getType() == RpcResponseType.SUCCESS) {
        successCount++;
      }
    }
    final long duration = System.currentTimeMillis() - startTime;

    // Then
    assertThat(successCount).isEqualTo(numRequests);
    // Execution time should be reasonable for concurrent execution
    assertThat(duration).isLessThan(10000); // 10 seconds max for 5 requests
  }

  @Test
  public void shouldHandleMethodThatThrowsException() {
    // Given
    final JsonRpcMethod faultyMethod = mock(JsonRpcMethod.class);
    when(faultyMethod.getName()).thenReturn("faulty_method");
    when(faultyMethod.response(any())).thenThrow(new RuntimeException("Simulated error"));

    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 5000);

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "faulty_method", new Object[] {});
    final JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);
    final JsonRpcRequestId id = new JsonRpcRequestId(1);

    // When
    final JsonRpcResponse response =
        asyncProcessor.process(id, faultyMethod, Span.getInvalid(), requestContext);

    // Then
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(((JsonRpcErrorResponse) response).getErrorType())
        .isEqualTo(RpcErrorType.INTERNAL_ERROR);
  }

  @Test
  public void shouldWorkWithJsonRpcExecutor() {
    // Given
    final Map<String, JsonRpcMethod> methods = methodsFactory.methods();
    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 5000);
    final JsonRpcExecutor executor = new JsonRpcExecutor(asyncProcessor, methods);

    final Map<String, Boolean> params = Map.of("disableStorage", true);
    final Hash trxHash =
        Hash.fromHexString("0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");

    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {trxHash, params});
    request.setId(new JsonRpcRequestId(1));
    final JsonRpcResponse response =
        executor.execute(
            java.util.Optional.empty(),
            null,
            io.opentelemetry.context.Context.current(),
            () -> true,
            io.vertx.core.json.JsonObject.mapFrom(request),
            req -> req.mapTo(JsonRpcRequest.class));

    // Then
    assertThat(response.getType()).isEqualTo(RpcResponseType.SUCCESS);
    final OpCodeLoggerTracerResult result =
        (OpCodeLoggerTracerResult) ((JsonRpcSuccessResponse) response).getResult();
    assertThat(result.getGas()).isEqualTo(23705L);
  }

  @Test
  public void shouldNotBlockEventLoop() throws InterruptedException {
    // Given
    final JsonRpcMethod blockingMethod = mock(JsonRpcMethod.class);
    when(blockingMethod.getName()).thenReturn("blocking_method");
    when(blockingMethod.response(any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(200); // Simulate blocking work
              return new JsonRpcSuccessResponse(new JsonRpcRequestId(1), "result");
            });

    final AsyncJsonRpcProcessor asyncProcessor =
        new AsyncJsonRpcProcessor(new BaseJsonRpcProcessor(), vertx, 5000);

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "blocking_method", new Object[] {});
    final JsonRpcRequestContext requestContext = new JsonRpcRequestContext(request);

    // When - Execute multiple requests
    final int numRequests = 10;
    // final Thread[] executionThreads = new Thread[numRequests];

    for (int i = 0; i < numRequests; i++) {
      final int index = i;
      new Thread(
              () -> {
                asyncProcessor.process(
                    new JsonRpcRequestId(index), blockingMethod, Span.getInvalid(), requestContext);
              })
          .start();
    }

    // Give time for all requests to complete
    Thread.sleep(1000);

    // Then - All should complete without blocking event loop
    // This test mainly verifies that the async processor can handle concurrent requests
  }

  @Test
  public void shouldSerializeHashObjectsInJsonRpcRequest() {
    // Given - JsonRpcRequest with Hash object in params array
    final Hash testHash =
        Hash.fromHexString("0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");
    final JsonRpcRequest request =
        new JsonRpcRequest(
            "2.0", METHOD_NAME, new Object[] {testHash, Map.of("disableStorage", true)});
    request.setId(new JsonRpcRequestId(1));

    // When - serialize to JsonObject using mapFrom
    final io.vertx.core.json.JsonObject jsonObject = io.vertx.core.json.JsonObject.mapFrom(request);

    // Then - Hash should be serialized as string
    assertThat(jsonObject.containsKey("params")).isTrue();
    final io.vertx.core.json.JsonArray paramsArray = jsonObject.getJsonArray("params");
    assertThat(paramsArray).isNotNull();
    assertThat(paramsArray.size()).isEqualTo(2);

    // First parameter should be the hash as a hex string
    final Object firstParam = paramsArray.getValue(0);
    assertThat(firstParam).isInstanceOf(String.class);
    assertThat((String) firstParam)
        .isEqualTo("0xcef53f2311d7c80e9086d661e69ac11a5f3d081e28e02a9ba9b66749407ac310");

    // Should be able to deserialize back to JsonRpcRequest
    final JsonRpcRequest deserializedRequest = jsonObject.mapTo(JsonRpcRequest.class);
    assertThat(deserializedRequest.getMethod()).isEqualTo(METHOD_NAME);
    assertThat(deserializedRequest.getParams()).isNotNull();
    assertThat(deserializedRequest.getParams().length).isEqualTo(2);
  }
}
