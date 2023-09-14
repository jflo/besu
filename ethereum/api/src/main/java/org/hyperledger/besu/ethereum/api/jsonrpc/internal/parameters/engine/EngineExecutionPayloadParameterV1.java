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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.CheckerUnsignedLongParameter;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import org.checkerframework.checker.signedness.qual.Unsigned;

@JsonSerialize(using = NewPayloadV1Serializer.class)
@JsonDeserialize(using = NewPayloadV1Deserializer.class)
public class EngineExecutionPayloadParameterV1 {
  private final Hash blockHash;
  private final Hash parentHash;
  private final Address feeRecipient;
  private final Hash stateRoot;
  private final @Unsigned long blockNumber;
  private final Bytes32 prevRandao;
  private final Wei baseFeePerGas;
  private final @Unsigned long gasLimit;
  private final @Unsigned long gasUsed;
  private final @Unsigned long timestamp;
  private final String extraData;
  private final Hash receiptsRoot;
  private final LogsBloomFilter logsBloom;
  private final List<String> transactions;

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
   */
  @JsonCreator
  public EngineExecutionPayloadParameterV1(
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
      @JsonProperty("transactions") final List<String> transactions) {
    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.feeRecipient = feeRecipient;
    this.stateRoot = stateRoot;
    this.blockNumber = blockNumber;
    this.baseFeePerGas = Wei.fromHexString(baseFeePerGas);
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.extraData = extraData;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.prevRandao = Bytes32.fromHexString(prevRandao);
    this.transactions = transactions;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public Hash getParentHash() {
    return parentHash;
  }

  public Address getFeeRecipient() {
    return feeRecipient;
  }

  public Hash getStateRoot() {
    return stateRoot;
  }

  public @Unsigned long getBlockNumber() {
    return blockNumber;
  }

  public Wei getBaseFeePerGas() {
    return baseFeePerGas;
  }

  public @Unsigned long getGasLimit() {
    return gasLimit;
  }

  public @Unsigned long getGasUsed() {
    return gasUsed;
  }

  public @Unsigned long getTimestamp() {
    return timestamp;
  }

  public String getExtraData() {
    return extraData;
  }

  public Hash getReceiptsRoot() {
    return receiptsRoot;
  }

  public LogsBloomFilter getLogsBloom() {
    return logsBloom;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public List<String> getTransactions() {
    return transactions;
  }
}
