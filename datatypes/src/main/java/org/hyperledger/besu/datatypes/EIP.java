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
package org.hyperledger.besu.datatypes;

import java.util.Optional;

/**
 * Ethereum Improvement Proposal (EIP) registry. This enum provides a type-safe way to reference
 * EIPs and query whether they are enabled in a protocol specification.
 *
 * <p>This enum is designed for future EIPs - historical EIPs can be added as needed when
 * implementing features that require them.
 */
public enum EIP {
  /** EIP-1559: Fee market change for ETH 1.0 chain */
  EIP_1559("FEE_MARKET", "Fee market change for ETH 1.0 chain"),
  /** EIP-3074: AUTH and AUTHCALL opcodes */
  EIP_3074("AUTH_AUTHCALL", "AUTH and AUTHCALL opcodes"),
  /** EIP-3198: BASEFEE opcode */
  EIP_3198("BASEFEE", "BASEFEE opcode"),
  /** EIP-4844: Shard Blob Transactions */
  EIP_4844("SHARD_BLOB_TRANSACTIONS", "Shard Blob Transactions"),
  /** EIP-6110: Supply validator deposits on chain */
  EIP_6110("DEPOSIT_REQUESTS", "Supply validator deposits on chain"),
  /** EIP-7002: Execution layer triggerable withdrawals */
  EIP_7002("WITHDRAWAL_REQUESTS", "Execution layer triggerable withdrawals"),
  /** EIP-7251: Increase the MAX_EFFECTIVE_BALANCE */
  EIP_7251("CONSOLIDATION_REQUESTS", "Increase the MAX_EFFECTIVE_BALANCE"),
  /** EIP-7702: Set EOA account code for one transaction */
  EIP_7702("SET_CODE_TRANSACTION", "Set EOA account code for one transaction"),
// Add more EIPs as they are proposed/accepted
;

  private final String shortName;
  private final String description;

  /**
   * Creates a new EIP enum constant.
   *
   * @param shortName short identifier for the EIP
   * @param description human-readable description of what the EIP does
   */
  EIP(final String shortName, final String description) {
    this.shortName = shortName;
    this.description = description;
  }

  /**
   * Gets the short name for this EIP.
   *
   * @return the short name
   */
  public String getShortName() {
    return shortName;
  }

  /**
   * Gets the description of what this EIP does.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the EIP number (e.g., 1559 for EIP_1559).
   *
   * @return the EIP number
   */
  public int getNumber() {
    return Integer.parseInt(name().substring(4)); // Extract number from EIP_XXXX
  }

  /**
   * Looks up an EIP by its number.
   *
   * @param number the EIP number (e.g., 1559)
   * @return Optional containing the EIP if it exists in the enum, empty otherwise
   */
  public static Optional<EIP> fromNumber(final int number) {
    final String enumName = "EIP_" + number;
    try {
      return Optional.of(valueOf(enumName));
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
