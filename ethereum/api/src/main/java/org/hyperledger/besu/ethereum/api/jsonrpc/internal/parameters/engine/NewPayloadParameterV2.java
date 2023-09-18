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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.checkerframework.checker.signedness.qual.Unsigned;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonSerialize(using = NewPayloadV2Serializer.class)
@JsonDeserialize(using = NewPayloadV2Deserializer.class)
public class NewPayloadParameterV2 extends NewPayloadParameterV1 {

  private final List<WithdrawalParameter> withdrawals;

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
   */
  @JsonCreator
  public NewPayloadParameterV2(
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("parentHash") final Hash parentHash,
      @JsonProperty("feeRecipient") final Address feeRecipient,
      @JsonProperty("stateRoot") final Hash stateRoot,
      @JsonProperty("blockNumber") final @Unsigned long blockNumber,
      @JsonProperty("baseFeePerGas") final String baseFeePerGas,
      @JsonProperty("gasLimit") final @Unsigned long gasLimit,
      @JsonProperty("gasUsed") final @Unsigned long gasUsed,
      @JsonProperty("timestamp") final @Unsigned long timestamp,
      @JsonProperty(value = "extraData", required = true) final String extraData,
      @JsonProperty("receiptsRoot") final Hash receiptsRoot,
      @JsonProperty("logsBloom") final LogsBloomFilter logsBloom,
      @JsonProperty("prevRandao") final String prevRandao,
      @JsonProperty("transactions") final List<String> transactions,
      @JsonProperty(value = "withdrawals", required = true) final List<WithdrawalParameter> withdrawals) {
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
        transactions);
    this.withdrawals = withdrawals;
  }

  public NewPayloadParameterV2(final NewPayloadParameterV1 initFrom, final List<WithdrawalParameter> withdrawals) {
    this(
        initFrom.getBlockHash(),
        initFrom.getParentHash(),
        initFrom.getFeeRecipient(),
        initFrom.getStateRoot(),
        initFrom.getBlockNumber(),
        initFrom.getBaseFeePerGas().toHexString(),
        initFrom.getGasLimit(),
        initFrom.getGasUsed(),
        initFrom.getTimestamp(),
        initFrom.getExtraData(),
        initFrom.getReceiptsRoot(),
        initFrom.getLogsBloom(),
        initFrom.getPrevRandao().toHexString(),
        initFrom.getTransactions(),
        withdrawals);

  }

  public List<WithdrawalParameter> getWithdrawals() {
    return withdrawals;
  }
}
