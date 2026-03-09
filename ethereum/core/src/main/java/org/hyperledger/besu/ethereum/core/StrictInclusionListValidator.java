/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict inclusion list validator per EIP-7805. Enforces that all inclusion list transactions are
 * present in the payload (in any order per the "anywhere-in-block" property), and that the total
 * byte size of the inclusion list does not exceed MAX_BYTES_PER_INCLUSION_LIST.
 */
public class StrictInclusionListValidator implements InclusionListValidator {

  private static final Logger LOG = LoggerFactory.getLogger(StrictInclusionListValidator.class);

  @Override
  public InclusionListValidationResult validate(
      final List<Bytes> payloadTransactions, final List<Bytes> inclusionListTransactions) {

    if (inclusionListTransactions == null || inclusionListTransactions.isEmpty()) {
      return InclusionListValidationResult.valid();
    }

    LOG.atDebug()
        .setMessage("Strict IL validation: {} IL txs, {} payload txs")
        .addArgument(inclusionListTransactions.size())
        .addArgument(payloadTransactions.size())
        .log();

    // Validate total byte size of inclusion list
    final int totalBytes = inclusionListTransactions.stream().mapToInt(Bytes::size).sum();
    if (totalBytes > InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST) {
      LOG.warn(
          "IL byte size exceeded: {} > {}",
          totalBytes,
          InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST);
      return InclusionListValidationResult.invalid(
          "Inclusion list exceeds MAX_BYTES_PER_INCLUSION_LIST: "
              + totalBytes
              + " > "
              + InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST);
    }

    // Validate all IL transactions are present in payload (anywhere-in-block per EIP-7805)
    final Set<Bytes> payloadTxSet = new HashSet<>(payloadTransactions);
    for (int i = 0; i < inclusionListTransactions.size(); i++) {
      final Bytes ilTx = inclusionListTransactions.get(i);
      if (!payloadTxSet.contains(ilTx)) {
        LOG.warn("IL unsatisfied: missing transaction at index {}", i);
        return InclusionListValidationResult.unsatisfied(
            "Inclusion list not satisfied: missing transaction at index "
                + i
                + " ("
                + ilTx.toHexString()
                + ")");
      }
    }

    return InclusionListValidationResult.valid();
  }
}
