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
 * Interface for validating inclusion list constraints per EIP-7805. Implementations determine
 * whether a payload satisfies the required inclusion list transactions.
 */
public interface InclusionListValidator {

  /**
   * Validates that the given payload satisfies the inclusion list constraints.
   *
   * @param payloadTransactions the transactions included in the execution payload
   * @param inclusionListTransactions the transactions required by the inclusion list
   * @return validation result with status VALID, INVALID, or UNSATISFIED
   */
  InclusionListValidationResult validate(
      List<Bytes> payloadTransactions, List<Bytes> inclusionListTransactions);
}
