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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV1;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

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
      NewPayloadParameterV1 retval =  new NewPayloadParameterV1(
              header.getHash(),
              header.getParentHash(),
              header.getCoinbase(),
              header.getStateRoot(),
              header.getNumber(),
              header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
              header.getGasLimit(),
              header.getGasUsed(),
              header.getTimestamp(),
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

    @Override
    protected BlockHeaderTestFixture createBlockHeaderTestFixture(
            final List<Transaction> maybeTransactions,
        final Optional<List<Withdrawal>> maybeWithdrawals,
        final Optional<List<Deposit>> maybeDeposits) {
      BlockHeader parentBlockHeader =
          new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
      return new BlockHeaderTestFixture()
              .baseFeePerGas(Wei.ONE)
              .parentHash(parentBlockHeader.getParentHash())
              .number(parentBlockHeader.getNumber() + 1)
              .timestamp(parentBlockHeader.getTimestamp() + 1)
              .extraData(Bytes.fromHexString("0xDEADBEEF0001"))
              .transactionsRoot(BodyValidation.transactionsRoot(maybeTransactions));
    }
}
