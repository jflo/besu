/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.CHAIN_HEAD_NOT_AVAILABLE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains the set of pending transactions received from JSON-RPC or other nodes. Transactions are
 * removed automatically when they are included in a block on the canonical chain and re-added if a
 * re-org removes them from the canonical chain again.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class TransactionPool implements BlockAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionPool.class);

  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";
  private final AbstractPendingTransactionsSorter pendingTransactions;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final TransactionBroadcaster transactionBroadcaster;
  private final MiningParameters miningParameters;
  private final LabelledMetric<Counter> duplicateTransactionCounter;
  private final TransactionPoolConfiguration configuration;

  public TransactionPool(
      final AbstractPendingTransactionsSorter pendingTransactions,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionBroadcaster transactionBroadcaster,
      final EthContext ethContext,
      final MiningParameters miningParameters,
      final MetricsSystem metricsSystem,
      final TransactionPoolConfiguration configuration) {
    this.pendingTransactions = pendingTransactions;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.transactionBroadcaster = transactionBroadcaster;
    this.miningParameters = miningParameters;
    this.configuration = configuration;

    duplicateTransactionCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_duplicates_total",
            "Total number of duplicate transactions received",
            "source");

    ethContext.getEthPeers().subscribeConnect(this::handleConnect);
  }

  void handleConnect(final EthPeer peer) {
    transactionBroadcaster.relayTransactionPoolTo(peer);
  }

  public ValidationResult<TransactionInvalidReason> addLocalTransaction(
      final Transaction transaction) {
    final ValidationResultAndAccount validationResult = validateLocalTransaction(transaction);
    if (validationResult.result.isValid()) {
      if (!configuration.getTxFeeCap().isZero()
          && minTransactionGasPrice(transaction).compareTo(configuration.getTxFeeCap()) > 0) {
        return ValidationResult.invalid(TransactionInvalidReason.TX_FEECAP_EXCEEDED);
      }
      final TransactionAddedStatus transactionAddedStatus =
          pendingTransactions.addLocalTransaction(transaction, validationResult.maybeAccount.get());
      if (!transactionAddedStatus.equals(ADDED)) {
        duplicateTransactionCounter.labels(LOCAL).inc();
        return ValidationResult.invalid(transactionAddedStatus.getInvalidReason().orElseThrow());
      }
      final Collection<Transaction> txs = singletonList(transaction);
      transactionBroadcaster.onTransactionsAdded(txs);
    }

    return validationResult.result;
  }

  private boolean effectiveGasPriceIsAboveConfiguredMinGasPrice(final Transaction transaction) {
    return transaction
        .getGasPrice()
        .map(Optional::of)
        .orElse(transaction.getMaxFeePerGas())
        .map(g -> g.greaterOrEqualThan(miningParameters.getMinTransactionGasPrice()))
        .orElse(false);
  }

  public void addRemoteTransactions(final Collection<Transaction> transactions) {
    final List<Transaction> addedTransactions = new ArrayList<>(transactions.size());
    LOG.trace("Adding {} remote transactions", transactions.size());
    for (final Transaction transaction : transactions) {
      if (pendingTransactions.containsTransaction(transaction.getHash())) {
        traceLambda(LOG, "Discard already present transaction {}", transaction::toTraceLog);
        // We already have this transaction, don't even validate it.
        duplicateTransactionCounter.labels(REMOTE).inc();
        continue;
      }
      final Wei transactionGasPrice = minTransactionGasPrice(transaction);
      if (transactionGasPrice.compareTo(miningParameters.getMinTransactionGasPrice()) < 0) {
        traceLambda(
            LOG,
            "Discard transaction {} below min gas price {}",
            transaction::toTraceLog,
            miningParameters::getMinTransactionGasPrice);
        pendingTransactions.signalInvalidTransaction(transaction);
        continue;
      }
      final ValidationResultAndAccount validationResult = validateRemoteTransaction(transaction);
      if (validationResult.result.isValid()) {
        final TransactionAddedStatus status =
            pendingTransactions.addRemoteTransaction(
                transaction, validationResult.maybeAccount.get());
        switch (status) {
          case ADDED:
            traceLambda(LOG, "Added remote transaction {}", transaction::toTraceLog);
            addedTransactions.add(transaction);
            break;
          case ALREADY_KNOWN:
            traceLambda(LOG, "Duplicate remote transaction {}", transaction::toTraceLog);
            duplicateTransactionCounter.labels(REMOTE).inc();
            break;
          default:
            traceLambda(LOG, "Transaction added status {}", status::name);
        }
      } else {
        traceLambda(
            LOG,
            "Discard invalid transaction {}, reason {}",
            transaction::toTraceLog,
            validationResult.result::getInvalidReason);
        pendingTransactions.signalInvalidTransaction(transaction);
      }
    }
    if (!addedTransactions.isEmpty()) {
      transactionBroadcaster.onTransactionsAdded(addedTransactions);
      traceLambda(
          LOG,
          "Added {} transactions to the pool, current pool size {}, content {}",
          addedTransactions::size,
          pendingTransactions::size,
          pendingTransactions::toTraceLog);
    }
  }

  public long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactions.subscribePendingTransactions(listener);
  }

  public void unsubscribePendingTransactions(final long id) {
    pendingTransactions.unsubscribePendingTransactions(id);
  }

  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return pendingTransactions.subscribeDroppedTransactions(listener);
  }

  public void unsubscribeDroppedTransactions(final long id) {
    pendingTransactions.unsubscribeDroppedTransactions(id);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    LOG.trace("Block added event {}", event);
    event.getAddedTransactions().forEach(pendingTransactions::transactionAddedToBlock);
    pendingTransactions.manageBlockAdded(event.getBlock());
    var readdTransactions = event.getRemovedTransactions();
    if (!readdTransactions.isEmpty()) {
      LOG.trace("Readding {} transactions from a block event", readdTransactions.size());
      addRemoteTransactions(readdTransactions);
    }
  }

  private MainnetTransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockNumber(protocolContext.getBlockchain().getChainHeadBlockNumber())
        .getTransactionValidator();
  }

  public AbstractPendingTransactionsSorter getPendingTransactions() {
    return pendingTransactions;
  }

  private ValidationResultAndAccount validateLocalTransaction(final Transaction transaction) {
    return validateTransaction(transaction, true);
  }

  private ValidationResultAndAccount validateRemoteTransaction(final Transaction transaction) {
    return validateTransaction(transaction, false);
  }

  private ValidationResultAndAccount validateTransaction(
      final Transaction transaction, final boolean isLocal) {

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader().orElse(null);
    if (chainHeadBlockHeader == null) {
      traceLambda(
          LOG,
          "rejecting transaction {} due to chain head not available yet",
          transaction::getHash);
      return ValidationResultAndAccount.invalid(CHAIN_HEAD_NOT_AVAILABLE);
    }

    final FeeMarket feeMarket =
        protocolSchedule.getByBlockNumber(chainHeadBlockHeader.getNumber()).getFeeMarket();

    // Check whether it's a GoQuorum transaction
    boolean goQuorumCompatibilityMode = getTransactionValidator().getGoQuorumCompatibilityMode();
    if (transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)) {
      final Optional<Wei> weiValue = ofNullable(transaction.getValue());
      if (weiValue.isPresent() && !weiValue.get().isZero()) {
        return ValidationResultAndAccount.invalid(
            TransactionInvalidReason.ETHER_VALUE_NOT_SUPPORTED);
      }
    }

    // allow local transactions to be below minGas as long as we are mining and the transaction is
    // executable:
    if ((!effectiveGasPriceIsAboveConfiguredMinGasPrice(transaction)
            && !miningParameters.isMiningEnabled())
        || (!feeMarket.satisfiesFloorTxCost(transaction))) {
      return ValidationResultAndAccount.invalid(TransactionInvalidReason.GAS_PRICE_TOO_LOW);
    }

    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator()
            .validate(
                transaction,
                chainHeadBlockHeader.getBaseFee(),
                TransactionValidationParams.transactionPool());
    if (!basicValidationResult.isValid()) {
      return new ValidationResultAndAccount(basicValidationResult);
    }

    if (isLocal
        && strictReplayProtectionShouldBeEnforceLocally(chainHeadBlockHeader)
        && transaction.getChainId().isEmpty()) {
      // Strict replay protection is enabled but the tx is not replay-protected
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURE_REQUIRED);
    }
    if (transaction.getGasLimit() > chainHeadBlockHeader.getGasLimit()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT,
          String.format(
              "Transaction gas limit of %s exceeds block gas limit of %s",
              transaction.getGasLimit(), chainHeadBlockHeader.getGasLimit()));
    }
    if (transaction.getType().equals(TransactionType.EIP1559) && !feeMarket.implementsBaseFee()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          "EIP-1559 transaction are not allowed yet");
    }

    return protocolContext
        .getWorldStateArchive()
        .getMutable(chainHeadBlockHeader.getStateRoot(), chainHeadBlockHeader.getHash(), false)
        .map(
            worldState -> {
              try {
                final Account senderAccount = worldState.get(transaction.getSender());
                return new ValidationResultAndAccount(
                    senderAccount,
                    getTransactionValidator()
                        .validateForSender(
                            transaction,
                            senderAccount,
                            TransactionValidationParams.transactionPool()));
              } catch (MerkleTrieException ex) {
                LOG.debug(
                    "MerkleTrieException while validating transaction for sender {}",
                    transaction.getSender());
                return ValidationResultAndAccount.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE);
              }
            })
        .orElseGet(() -> ValidationResultAndAccount.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE));
  }

  private boolean strictReplayProtectionShouldBeEnforceLocally(
      final BlockHeader chainHeadBlockHeader) {
    return configuration.getStrictTransactionReplayProtectionEnabled()
        && protocolSchedule.getChainId().isPresent()
        && transactionReplaySupportedAtBlock(chainHeadBlockHeader);
  }

  private boolean transactionReplaySupportedAtBlock(final BlockHeader block) {
    return protocolSchedule.getByBlockNumber(block.getNumber()).isReplayProtectionSupported();
  }

  public Optional<Transaction> getTransactionByHash(final Hash hash) {
    return pendingTransactions.getTransactionByHash(hash);
  }

  private Optional<BlockHeader> getChainHeadBlockHeader() {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    return blockchain.getBlockHeader(blockchain.getChainHeadHash());
  }

  private Wei minTransactionGasPrice(final Transaction transaction) {
    return getChainHeadBlockHeader()
        .map(
            chainHeadBlockHeader ->
                protocolSchedule
                    .getByBlockNumber(chainHeadBlockHeader.getNumber())
                    .getFeeMarket()
                    .minTransactionPriceInNextBlock(transaction, chainHeadBlockHeader::getBaseFee))
        .orElse(Wei.ZERO);
  }

  public interface TransactionBatchAddedListener {

    void onTransactionsAdded(Iterable<Transaction> transactions);
  }

  private static class ValidationResultAndAccount {
    final ValidationResult<TransactionInvalidReason> result;
    final Optional<Account> maybeAccount;

    ValidationResultAndAccount(
        final Account account, final ValidationResult<TransactionInvalidReason> result) {
      this.result = result;
      this.maybeAccount = Optional.of(account);
    }

    ValidationResultAndAccount(final ValidationResult<TransactionInvalidReason> result) {
      this.result = result;
      this.maybeAccount = Optional.empty();
    }

    static ValidationResultAndAccount invalid(
        final TransactionInvalidReason reason, final String message) {
      return new ValidationResultAndAccount(ValidationResult.invalid(reason, message));
    }

    static ValidationResultAndAccount invalid(final TransactionInvalidReason reason) {
      return new ValidationResultAndAccount(ValidationResult.invalid(reason));
    }
  }
}
