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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"code", "message", "data"})
public class EngineNewPayloadError {
  private final ExecutionEngineJsonRpcMethod.ExecutionError executionError;
  private final Data data;

  public EngineNewPayloadError(final ExecutionEngineJsonRpcMethod.ExecutionError executionError) {
    this(executionError, null);
  }

  public EngineNewPayloadError(
      final ExecutionEngineJsonRpcMethod.ExecutionError executionError, final String dataError) {
    this.executionError = executionError;

    if (dataError == null || dataError.isEmpty()) {
      this.data = null;
    } else {
      this.data = new Data(dataError);
    }
  }

  @JsonGetter(value = "code")
  public int getErrorCode() {
    return executionError.getCode();
  }

  @JsonGetter(value = "message")
  public String getErrorMessage() {
    return executionError.name();
  }

  @JsonGetter(value = "data")
  public Data getData() {
    return data;
  }

  public static class Data {
    private final String dataError;

    public Data(final String dataError) {
      this.dataError = dataError;
    }

    @JsonGetter(value = "err")
    public String getDataError() {
      return dataError;
    }
  }
}
