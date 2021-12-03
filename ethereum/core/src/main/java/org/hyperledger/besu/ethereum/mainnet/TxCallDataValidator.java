/*
 * Copyright Hyperledger Besu Contributors
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TxCallDataValidator extends BaseFeeBlockBodyValidator {

  public static final int BASE_MAX_CALLDATA_PER_BLOCK = 1048576;
  public static final int CALLDATA_PER_TX_STIPEND = 300;
  private static final Logger LOG = LogManager.getLogger(TxCallDataValidator.class);

  public TxCallDataValidator(final ProtocolSchedule protocolSchedule) {
    super(protocolSchedule);
  }

  @VisibleForTesting
  boolean validateTransactionDataLimit(final Block block) {
    // sub-optimal placement, multiple loops over tx list, see parent class.
    long callTotal;
    Integer limit =
        BASE_MAX_CALLDATA_PER_BLOCK
            + (block.getBody().getTransactions().size() * CALLDATA_PER_TX_STIPEND);
    List<Transaction> txs = block.getBody().getTransactions();
    callTotal =
        txs.stream()
            .map(
                tx -> {
                  return tx.getPayload().size();
                })
            .reduce(0, Integer::sum);
    if (callTotal > limit) {
      LOG.debug(
          "invalid block {} with {} bytes of calldata over limit of {}",
          block.getHeader().getNumber(),
          callTotal,
          limit);
      return false;
    }
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "valid block {} with {} bytes of calldata within limit of {}",
          block.getHeader().getNumber(),
          callTotal,
          limit);
    }
    return true;
  }

  @Override
  public boolean validateBodyLight(
      final ProtocolContext context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode ommerValidationMode) {

    return super.validateBodyLight(context, block, receipts, ommerValidationMode)
        && validateTransactionDataLimit(block);
  }
}
