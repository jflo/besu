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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameterTestFixture.WITHDRAWAL_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.util.Lists;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV2;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EngineNewPayloadV2Test extends EngineNewPayloadV1Test {

  public EngineNewPayloadV2Test() {}

  @Override
  @BeforeEach
  public void before() {
    super.before();
    this.method =
        new EngineNewPayloadV2(
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
    assertThat(method.getName()).isEqualTo("engine_newPayloadV2");
  }



  @Test
  public void shouldReturnValidIfWithdrawalsIsNotNull_WhenWithdrawalsAllowed() throws JsonProcessingException {
    final List<WithdrawalParameter> withdrawalsParam = List.of(WITHDRAWAL_PARAM_1);
    final List<Withdrawal> withdrawals = List.of(WITHDRAWAL_PARAM_1.toWithdrawal());
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.of(withdrawals),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp =
        respondTo(
            new Object[] {
              createNewPayloadParam(mockHeader, Collections.emptyList(), withdrawalsParam)
            });

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnValidIfWithdrawalsIsNull_WhenWithdrawalsProhibited() {
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp =
        respondTo(
            new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNotNull_WhenWithdrawalsProhibited() throws JsonProcessingException {
    final List<WithdrawalParameter> withdrawals = List.of();
    lenient()
        .when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());

    JsonRpcResponse resp =
        respondTo(
            new Object[] {
              createNewPayloadParam(
                  createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader(),
                  Collections.emptyList(),
                  withdrawals)
            });

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidIfWithdrawalsIsNull_WhenWithdrawalsAllowed() throws JsonProcessingException {
    final List<WithdrawalParameter> withdrawals = null;
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());

    var resp =
        respondTo(
            new Object[] {
              createNewPayloadParam(
                  createBlockHeaderTestFixture(Collections.emptyList(),Optional.empty(), Optional.empty()).buildHeader(),
                  Collections.emptyList(),
                  withdrawals)
            });

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  protected String createNewPayloadParam(final BlockHeader header, final List<String> txs) {
    return super.createNewPayloadParam(header, txs);
  }


  @SuppressWarnings({"unchecked", "signedness:argument"})
  protected String createNewPayloadParam(
      final BlockHeader header,
      final List<String> txs,
      final List<WithdrawalParameter> withdrawals)  {
    ObjectMapper mapper = new ObjectMapper();
    NewPayloadParameterV2 retval = new NewPayloadParameterV2(
        header.getHash(),
        header.getParentHash(),
        header.getCoinbase(),
        header.getStateRoot(),
        header.getNumber(),
        header.getBaseFee().map(w -> w.toHexString()).orElse("0x0"),
        header.getGasLimit(),
        header.getGasUsed(),
        header.getTimestamp(),
        header.getExtraData() == null ? null : header.getExtraData().toHexString(),
        header.getReceiptsRoot(),
        header.getLogsBloom(),
        header.getPrevRandao().map(Bytes32::toHexString).orElse("0x0"),
        txs,
        withdrawals);
    try {
      return mapper.writeValueAsString(retval);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected BlockHeaderTestFixture createBlockHeaderTestFixture(
          final List<Transaction> maybeTransactions,
          final Optional<List<Withdrawal>> maybeWithdrawals,
          final Optional<List<Deposit>> maybeDeposits) {
    BlockHeader parentBlockHeader =
            new BlockHeaderTestFixture().baseFeePerGas(Wei.ONE).buildHeader();
    BlockHeaderTestFixture testFixture = new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 1)
            .extraData(Bytes.fromHexString("0xDEADBEEF"))
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
            .transactionsRoot(BodyValidation.transactionsRoot(maybeTransactions));

    if(maybeWithdrawals.isPresent() && maybeWithdrawals.get().size() > 0) {
      testFixture.withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null));
    }
    return testFixture;
  }
}
