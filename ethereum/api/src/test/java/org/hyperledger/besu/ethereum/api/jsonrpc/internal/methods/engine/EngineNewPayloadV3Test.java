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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV3;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EngineNewPayloadV3Test extends EngineNewPayloadV2Test {

  public EngineNewPayloadV3Test() {}

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV3");
  }

  @BeforeEach
  @Override
  public void before() {
    super.before();
    maybeParentBeaconBlockRoot = Optional.of(Bytes32.ZERO);
    this.method =
        new EngineNewPayloadV3(
            vertx,
            protocolSchedule,
            protocolContext,
            mergeCoordinator,
            ethPeers,
            engineCallListener);
    lenient().when(protocolSpec.getGasCalculator()).thenReturn(new CancunGasCalculator());
  }

  @Test
  public void shouldInvalidVersionedHash_whenShortVersionedHash() {
    final Bytes shortHash = Bytes.fromHexString("0x" + "69".repeat(31));

    final NewPayloadParameterV3 payload = mock(NewPayloadParameterV3.class);
    when(payload.getTimestamp()).thenReturn(cancunHardfork.milestone());
    when(payload.getExcessBlobGas()).thenReturn("99");
    when(payload.getBlobGasUsed()).thenReturn(9l);

    final JsonRpcResponse badParam =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName(),
                    new Object[] {
                      payload,
                      List.of(shortHash.toHexString()),
                      "0x0000000000000000000000000000000000000000000000000000000000000000"
                    })));
    final EnginePayloadStatusResult res = fromSuccessResp(badParam);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Invalid versionedHash");
  }

  @Test
  public void shouldValidVersionedHash_whenListIsEmpty() {
    final BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.empty(),
            Optional.empty());
    final NewPayloadParameterV3 payload =
        createNewPayloadParamV3(mockHeader, Collections.emptyList(), Collections.emptyList());

    JsonRpcRequest req =
        new JsonRpcRequest(
            "2.0",
            "engine_newPayloadV3",
            new Object[] {
              payload,
              Optional.of(List.of()),
              Optional.of("0x0000000000000000000000000000000000000000000000000000000000000000")
            });
    JsonRpcRequestContext ctx = new JsonRpcRequestContext(req);
    ValidationResult<RpcErrorType> res = method.validateRequest(ctx);
    assertThat(res.isValid()).isTrue();
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
            .timestamp(super.cancunHardfork.milestone())
            .buildHeader();

    when(blockchain.getBlockHeader(parentBlockHeader.getBlockHash()))
        .thenReturn(Optional.of(parentBlockHeader));
    when(protocolContext.getBlockchain()).thenReturn(blockchain);

    return    new BlockHeaderTestFixture()
            .baseFeePerGas(Wei.ONE)
            .parentHash(parentBlockHeader.getParentHash())
            .number(parentBlockHeader.getNumber() + 1)
            .timestamp(parentBlockHeader.getTimestamp() + 12)
            .withdrawalsRoot(maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null))
            .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null))
            .transactionsRoot(BodyValidation.transactionsRoot(maybeTransactions))
            .excessBlobGas(BlobGas.ZERO)
            .blobGasUsed(0L)
            .parentBeaconBlockRoot(
                maybeParentBeaconBlockRoot.isPresent() ? maybeParentBeaconBlockRoot : null);

  }

  @Test
  public void shouldValidateBlobGasUsedCorrectly() {
    // V3 must return error if null blobGasUsed
    BlockHeader blockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.empty(), Optional.empty()).buildHeader();

    var resp =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    "engine_newPayloadV3",
                    new Object[] {
                      createNewPayloadParamV3(
                          blockHeader, Collections.emptyList(), Collections.emptyList()),
                      Collections.emptyList(),
                      Collections.emptyList()
                    })));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    assertThat(jsonRpcError.getData()).isEqualTo("Missing blob gas fields");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldValidateExcessBlobGasCorrectly() {
    // V3 must return error if null excessBlobGas
    BlockHeader blockHeader =
        createBlockHeaderTestFixture(Collections.emptyList(), Optional.empty(), Optional.empty()).excessBlobGas(null).blobGasUsed(100L).buildHeader();

    var resp =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    "engine_newPayloadV3",
                    new Object[] {
                      createNewPayloadParamV3(
                          blockHeader, Collections.emptyList(), Collections.emptyList()),
                      Collections.emptyList(),
                      Collections.emptyList()
                    })));

    final JsonRpcError jsonRpcError = fromErrorResp(resp);
    assertThat(jsonRpcError.getCode()).isEqualTo(INVALID_PARAMS.getCode());
    assertThat(jsonRpcError.getData()).isEqualTo("Missing blob gas fields");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected String createNewPayloadParam(
          final BlockHeader header,
          final List<String> txs) {
    ObjectMapper mapper = new ObjectMapper();
    NewPayloadParameterV3 retval = createNewPayloadParamV3(header, txs, null);
    try {
      return mapper.writeValueAsString(retval);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
  @SuppressWarnings("signedness:argument")
  protected NewPayloadParameterV3 createNewPayloadParamV3(
      final BlockHeader header,
      final List<String> txs,
      final List<WithdrawalParameter> withdrawals) {
    return new NewPayloadParameterV3(
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
        header.getExcessBlobGas().orElse(BlobGas.ZERO).toHexString());
  }
}
