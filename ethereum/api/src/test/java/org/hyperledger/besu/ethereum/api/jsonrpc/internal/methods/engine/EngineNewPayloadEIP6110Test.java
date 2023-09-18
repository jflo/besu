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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameterTestFixture.DEPOSIT_PARAM_1;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterEIP6110;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineNewPayloadEIP6110Test extends EngineNewPayloadV3Test {
  private static final Address depositContractAddress =
      Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");

  public EngineNewPayloadEIP6110Test() {}

  @BeforeEach
  @Override
  public void before() {
    super.before();
    maybeParentBeaconBlockRoot = Optional.of(Bytes32.ZERO);
    this.method =
        new EngineNewPayloadEIP6110(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
    lenient()
        .when(protocolSchedule.hardforkFor(any()))
        .thenReturn(Optional.of(super.cancunHardfork));
    lenient().when(protocolSpec.getGasCalculator()).thenReturn(new CancunGasCalculator());
  }

  @Override
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadEIP6110");
  }

  @Test
  public void shouldReturnValidIfDepositsIsNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = null;
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());

    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));

    var resp =
        respondTo(
            new Object[] {
              createNewEngineParam(mockHeader, Collections.emptyList(), null, deposits),
              Collections.emptyList(),
              Bytes32.ZERO.toHexString()
            });

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNull_WhenDepositsAllowed() {
    final List<DepositParameter> deposits = null;
    lenient()
        .when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));

    var resp =
        respondTo(
            new Object[] {
              createNewEngineParam(
                  createBlockHeaderTestFixture(Collections.emptyList(), Optional.empty(), Optional.empty()).buildHeader(),
                  Collections.emptyList(),
                  null,
                  deposits)
            });

    assertThat(fromErrorResp(resp).getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfDepositsIsNotNull_WhenDepositsAllowed() {
    final List<DepositParameter> depositsParam = List.of(DEPOSIT_PARAM_1);
    final List<Deposit> deposits = List.of(DEPOSIT_PARAM_1.toDeposit());
    when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.AllowedDeposits(depositContractAddress));
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.of(deposits));
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(mockHeader))
        .thenReturn(Optional.of(mockHeader.getHash()));
    var resp =
        respondTo(
            new Object[] {
              createNewEngineParam(mockHeader, Collections.emptyList(), null, depositsParam)
            });

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidIfDepositsIsNotNull_WhenDepositsProhibited() {
    final List<DepositParameter> deposits = List.of();
    lenient()
        .when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());

    var resp =
        respondTo(
            new Object[] {
              createNewEngineParam(
                  createBlockHeaderTestFixture(Collections.emptyList(), Optional.empty(), Optional.of(Collections.emptyList())).buildHeader(),
                  Collections.emptyList(),
                  null,
                  deposits)
            });

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  @SuppressWarnings("signedness:argument")
  protected BlockHeaderTestFixture createBlockHeaderTestFixture(
          final List<Transaction> maybeTransactions,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits) {
    BlockHeader parentBlockHeader =
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .timestamp(super.experimentalHardfork.milestone())
            .excessBlobGas(BlobGas.ZERO)
            .blobGasUsed(100L)
            .buildHeader();

    return
        new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 1)
            .withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null))
            .excessBlobGas(BlobGas.ZERO)
            .blobGasUsed(100L)
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
                .transactionsRoot(BodyValidation.transactionsRoot(maybeTransactions))
            .parentBeaconBlockRoot(
                maybeParentBeaconBlockRoot.isPresent() ? maybeParentBeaconBlockRoot : null);

  }

  @SuppressWarnings("signedness:argument")
  protected NewPayloadParameterEIP6110 createNewEngineParam(
      final BlockHeader header,
      final List<String> txs,
      final List<WithdrawalParameter> withdrawals,
      final List<DepositParameter> deposits) {
    return new NewPayloadParameterEIP6110(
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
        withdrawals,
        header.getBlobGasUsed().orElse(0L),
        header.getExcessBlobGas().map(BlobGas::toHexString).orElse("0x0"),
        deposits);
  }
}
