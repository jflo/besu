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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.CheckerUnsignedLongParameter;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.checkerframework.checker.signedness.qual.Unsigned;

public class EngineExecutionPayloadParameterV3 extends EngineExecutionPayloadParameterV2 {

  private final @Unsigned long blobGasUsed;
  private final String excessBlobGas;

  /**
   * Creates an instance of EnginePayloadParameter.
   *
   * @param blockHash DATA, 32 Bytes
   * @param parentHash DATA, 32 Bytes
   * @param feeRecipient DATA, 20 Bytes
   * @param stateRoot DATA, 32 Bytes
   * @param blockNumber QUANTITY, 64 Bits
   * @param baseFeePerGas QUANTITY, 256 Bits
   * @param gasLimit QUANTITY, 64 Bits
   * @param gasUsed QUANTITY, 64 Bits
   * @param timestamp QUANTITY, 64 Bits
   * @param extraData DATA, 0 to 32 Bytes
   * @param receiptsRoot DATA, 32 Bytes
   * @param logsBloom DATA, 256 Bytes
   * @param prevRandao DATA, 32 Bytes
   * @param transactions Array of DATA
   * @param withdrawals Array of Withdrawal
   * @param withdrawals Array of Withdrawal
   * @param blobGasUsed QUANTITY, 64 Bits
   * @param excessBlobGas QUANTITY, 64 Bits
   */
  @JsonCreator
  public EngineExecutionPayloadParameterV3(
      @JsonProperty(value = "blockHash", required = true) final Hash blockHash,
      @JsonProperty(value = "parentHash", required = true) final Hash parentHash,
      @JsonProperty(value = "feeRecipient", required = true) final Address feeRecipient,
      @JsonProperty(value = "stateRoot", required = true) final Hash stateRoot,
      @JsonProperty(value = "blockNumber", required = true)
          final @Unsigned long blockNumber,
      @JsonProperty(value = "baseFeePerGas", required = true) final String baseFeePerGas,
      @JsonProperty(value = "gasLimit", required = true)
          final @Unsigned long gasLimit,
      @JsonProperty(value = "gasUsed", required = true) final @Unsigned long gasUsed,
      @JsonProperty(value = "timestamp", required = true)
          final @Unsigned long timestamp,
      @JsonProperty(value = "extraData", required = true) final String extraData,
      @JsonProperty(value = "receiptsRoot", required = true) final Hash receiptsRoot,
      @JsonProperty(value = "logsBloom", required = true) final LogsBloomFilter logsBloom,
      @JsonProperty(value = "prevRandao", required = true) final String prevRandao,
      @JsonProperty(value = "transactions", required = true) final List<String> transactions,
      @JsonProperty(value = "withdrawals", required = true)
          final List<WithdrawalParameter> withdrawals,
      @JsonProperty(value = "blobGasUsed", required = true)
          final @Unsigned long blobGasUsed,
      @JsonProperty(value = "excessBlobGas", required = true) final String excessBlobGas) {

    super(
        blockHash,
        parentHash,
        feeRecipient,
        stateRoot,
        blockNumber,
        baseFeePerGas,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        receiptsRoot,
        logsBloom,
        prevRandao,
        transactions,
        withdrawals);
    this.blobGasUsed = blobGasUsed;
    this.excessBlobGas = excessBlobGas;
  }

  public @Unsigned Long getBlobGasUsed() {
    return blobGasUsed;
  }

  public String getExcessBlobGas() {
    return excessBlobGas;
  }
}
