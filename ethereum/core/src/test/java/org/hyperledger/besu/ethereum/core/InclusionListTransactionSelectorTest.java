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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class InclusionListTransactionSelectorTest {

  @Test
  void enabledSelectorReturnsTransactions() {
    final Bytes tx1 = Bytes.fromHexString("0xaa");
    final Bytes tx2 = Bytes.fromHexString("0xbb");

    final InclusionListTransactionSelector selector =
        new InclusionListTransactionSelector() {
          @Override
          public List<Bytes> selectTransactions(
              final Hash parentHash,
              final List<Transaction> mempoolTransactions,
              final int maxBytes) {
            return List.of(tx1, tx2);
          }

          @Override
          public boolean isEnabled() {
            return true;
          }
        };

    assertThat(selector.isEnabled()).isTrue();
    assertThat(selector.selectTransactions(Hash.ZERO, Collections.emptyList(), 8192))
        .containsExactly(tx1, tx2);
  }

  @Test
  void disabledSelectorReturnsEmptyList() {
    final InclusionListTransactionSelector selector =
        new InclusionListTransactionSelector() {
          @Override
          public List<Bytes> selectTransactions(
              final Hash parentHash,
              final List<Transaction> mempoolTransactions,
              final int maxBytes) {
            return Collections.emptyList();
          }

          @Override
          public boolean isEnabled() {
            return false;
          }
        };

    assertThat(selector.isEnabled()).isFalse();
    assertThat(selector.selectTransactions(Hash.ZERO, Collections.emptyList(), 8192)).isEmpty();
  }

  @Test
  void selectTransactionsReceivesCorrectParameters() {
    final Hash expectedHash = Hash.fromHexStringLenient("0x1234");
    final int expectedMaxBytes = 4096;

    final InclusionListTransactionSelector selector =
        new InclusionListTransactionSelector() {
          @Override
          public List<Bytes> selectTransactions(
              final Hash parentHash,
              final List<Transaction> mempoolTransactions,
              final int maxBytes) {
            assertThat(parentHash).isEqualTo(expectedHash);
            assertThat(mempoolTransactions).isEmpty();
            assertThat(maxBytes).isEqualTo(expectedMaxBytes);
            return Collections.emptyList();
          }

          @Override
          public boolean isEnabled() {
            return true;
          }
        };

    selector.selectTransactions(expectedHash, Collections.emptyList(), expectedMaxBytes);
  }
}
