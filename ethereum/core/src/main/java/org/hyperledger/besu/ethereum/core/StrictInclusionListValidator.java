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

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * Strict inclusion list validator per EIP-7805. Enforces that all inclusion list transactions
 * appear in the payload in order (allowing interleaved non-IL transactions), and that the total
 * byte size of the inclusion list does not exceed MAX_BYTES_PER_INCLUSION_LIST.
 */
public class StrictInclusionListValidator implements InclusionListValidator {

  @Override
  public InclusionListValidationResult validate(
      final List<Bytes> payloadTransactions, final List<Bytes> inclusionListTransactions) {

    if (inclusionListTransactions == null || inclusionListTransactions.isEmpty()) {
      return InclusionListValidationResult.valid();
    }

    // Validate total byte size of inclusion list
    final int totalBytes = inclusionListTransactions.stream().mapToInt(Bytes::size).sum();
    if (totalBytes > InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST) {
      return InclusionListValidationResult.invalid(
          "Inclusion list exceeds MAX_BYTES_PER_INCLUSION_LIST: "
              + totalBytes
              + " > "
              + InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST);
    }

    // Validate all IL transactions appear in payload in order (allowing interleaved txs)
    int ilIndex = 0;
    for (final Bytes payloadTx : payloadTransactions) {
      if (ilIndex < inclusionListTransactions.size()
          && payloadTx.equals(inclusionListTransactions.get(ilIndex))) {
        ilIndex++;
      }
    }

    if (ilIndex < inclusionListTransactions.size()) {
      return InclusionListValidationResult.unsatisfied(
          "Inclusion list not satisfied: missing transaction at index "
              + ilIndex
              + " ("
              + inclusionListTransactions.get(ilIndex).toHexString()
              + ")");
    }

    return InclusionListValidationResult.valid();
  }
}
