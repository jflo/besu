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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.checkerframework.checker.signedness.qual.Unsigned;

/**
 * Represents all possible parameters for a new payload request in the Ethereum engine, normalized
 * for internal use.
 */
public class EngineNewPayloadRequest {

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
  private final Optional<List<WithdrawalParameter>> withdrawals;
  private final Optional<@Unsigned Long> blobGasUsed;
  private final Optional<String> excessBlobGas;
  private final Optional<List<String>> blobVersionedHashes;
  private final Optional<String> parentBeaconBlockRoot;
  private final Optional<List<DepositParameter>> deposits;

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
   * @param blobGasUsed QUANTITY, 64 Bits
   * @param excessBlobGas QUANTITY, 64 Bits
   * @param blobVersionedHashes Array of DATA
   * @param parentBeaconBlockRoot DATA, 32 Bytes
   */
  public EngineNewPayloadRequest(
      final Hash blockHash,
      final Hash parentHash,
      final Address feeRecipient,
      final Hash stateRoot,
      final @Unsigned long blockNumber,
      final Wei baseFeePerGas,
      final @Unsigned long gasLimit,
      final @Unsigned long gasUsed,
      final @Unsigned long timestamp,
      final String extraData,
      final Hash receiptsRoot,
      final LogsBloomFilter logsBloom,
      final Bytes32 prevRandao,
      final List<String> transactions,
      final Optional<List<WithdrawalParameter>> withdrawals,
      final Optional<@Unsigned Long> blobGasUsed,
      final Optional<String> excessBlobGas,
      final Optional<List<String>> blobVersionedHashes,
      final Optional<String> parentBeaconBlockRoot,
      final Optional<List<DepositParameter>> deposits) {

    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.feeRecipient = feeRecipient;
    this.stateRoot = stateRoot;
    this.blockNumber = blockNumber;
    this.baseFeePerGas = baseFeePerGas;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.extraData = extraData;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.prevRandao = prevRandao;
    this.transactions = transactions;
    this.withdrawals = withdrawals;
    this.blobGasUsed = blobGasUsed;
    this.excessBlobGas = excessBlobGas;
    this.blobVersionedHashes = blobVersionedHashes;
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
    this.deposits = deposits;
  }

  /**
   * Returns the block hash.
   *
   * @return the block hash
   */
  public Hash getBlockHash() {
    return blockHash;
  }

  /**
   * Returns the parent hash.
   *
   * @return the parent hash
   */
  public Hash getParentHash() {
    return parentHash;
  }

  /**
   * Returns the fee recipient.
   *
   * @return the fee recipient
   */
  public Address getFeeRecipient() {
    return feeRecipient;
  }

  /**
   * Returns the state root.
   *
   * @return the state root
   */
  public Hash getStateRoot() {
    return stateRoot;
  }
  /**
   * Returns the block number.
   *
   * @return the block number
   */
  public @Unsigned long getBlockNumber() {
    return blockNumber;
  }
  /**
   * Returns the previous randao.
   *
   * @return the previous randao
   */
  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  /**
   * Returns the base fee per gas.
   *
   * @return the base fee per gas
   */
  public Wei getBaseFeePerGas() {
    return baseFeePerGas;
  }
  /**
   * Returns the gas limit.
   *
   * @return the gas limit
   */
  public @Unsigned long getGasLimit() {
    return gasLimit;
  }
  /**
   * Returns the gas used.
   *
   * @return the gas used
   */
  public @Unsigned long getGasUsed() {
    return gasUsed;
  }

  /**
   * Returns the timestamp.
   *
   * @return the timestamp
   */
  public @Unsigned long getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the extra data.
   *
   * @return the extra data
   */
  public String getExtraData() {
    return extraData;
  }

  /**
   * Returns the receipts root.
   *
   * @return the receipts root
   */
  public Hash getReceiptsRoot() {
    return receiptsRoot;
  }

  /**
   * Returns the logs bloom.
   *
   * @return the logs bloom
   */
  public LogsBloomFilter getLogsBloom() {
    return logsBloom;
  }

  /**
   * Returns the transactions.
   *
   * @return the transactions
   */
  public List<String> getTransactions() {
    return transactions;
  }

  /**
   * Returns the withdrawals.
   *
   * @return the withdrawals
   */
  public Optional<List<WithdrawalParameter>> getWithdrawals() {
    return withdrawals;
  }

  /**
   * Returns the blob gas used.
   *
   * @return the blob gas used
   */
  public Optional<@Unsigned Long> getBlobGasUsed() {
    return blobGasUsed;
  }

  /**
   * Returns the excess blob gas.
   *
   * @return the excess blob gas
   */
  public Optional<String> getExcessBlobGas() {
    return excessBlobGas;
  }

  /**
   * Returns the blob versioned hashes.
   *
   * @return the blob versioned hashes
   */
  public Optional<List<String>> getBlobVersionedHashes() {
    return blobVersionedHashes;
  }

  /**
   * Returns the parent beacon block root.
   *
   * @return the parent beacon block root
   */
  public Optional<String> getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }

  /**
   * Returns the deposits.
   *
   * @return the deposits
   */
  public Optional<List<DepositParameter>> getDeposits() {
    return deposits;
  }
}
