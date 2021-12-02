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
package org.hyperledger.besu.evm.gascalculator;

import org.hyperledger.besu.evm.Gas;

import org.apache.tuweni.bytes.Bytes;

public class PhillyGasCalculator extends LondonGasCalculator {

  public static final long BLOCK_NUMBER = 13773002L;
  public static final String BLOCK_HASH_HEX = "0x39a0fe3a";
  public static final Bytes BLOCK_HASH = Bytes.fromHexString(BLOCK_HASH_HEX);
  private static final Gas PHILLY_TX_DATA_COST = Gas.of(3L);
  private static final Gas TX_BASE_COST = Gas.of(21_000L);

  @Override
  public Gas transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreation) {
    Gas cost = TX_BASE_COST.plus(PHILLY_TX_DATA_COST.times(payload.size()));

    return isContractCreation ? cost.plus(txCreateExtraGasCost()) : cost;
  }
}
