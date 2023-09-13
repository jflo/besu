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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.CheckerUnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.EngineExecutionPayloadParameterV1;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineNewPayloadV1Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV1Test() {}

  @Override
  @BeforeEach
  public void before() {
    super.before();
    this.method =
        new EngineNewPayloadV1(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV1");
  }

  @Override
  protected ExecutionEngineJsonRpcMethod.EngineStatus getExpectedInvalidBlockHashStatus() {
    return INVALID_BLOCK_HASH;
  }

  @Override
  @SuppressWarnings({"unchecked", "signedness:argument"})
  protected String createNewPayloadParam(final BlockHeader header, final List<String> txs) {
    {
      ObjectMapper mapper = new ObjectMapper();
      EngineExecutionPayloadParameterV1 retval =  new EngineExecutionPayloadParameterV1(
              header.getHash(),
              header.getParentHash(),
              header.getCoinbase(),
              header.getStateRoot(),
              new CheckerUnsignedLongParameter(header.getNumber()),
              header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
              new CheckerUnsignedLongParameter(header.getGasLimit()),
              new CheckerUnsignedLongParameter(header.getGasUsed()),
              new CheckerUnsignedLongParameter(header.getTimestamp()),
              header.getExtraData() == null || header.getExtraData().isEmpty() ? null : header.getExtraData().toHexString(),
              header.getReceiptsRoot(),
              header.getLogsBloom(),
              header.getPrevRandao().map(Bytes32::toHexString).orElse("0x0"),
              txs);
      try {
        return mapper.writeValueAsString(retval);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }

    }
  }
}
