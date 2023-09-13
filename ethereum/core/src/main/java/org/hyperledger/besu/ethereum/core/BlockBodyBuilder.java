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

package org.hyperledger.besu.ethereum.core;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BlockBodyBuilder {
  private List<Transaction> transactions;
  private List<BlockHeader> ommers;
  private Optional<List<Withdrawal>> withdrawals;
  private Optional<List<Deposit>> deposits;

  public BlockBodyBuilder() {
    this.transactions = Collections.emptyList();
    this.ommers = Collections.emptyList();
    this.withdrawals = Optional.empty();
    this.deposits = Optional.empty();
  }

  public BlockBodyBuilder transactions(final List<Transaction> transactions) {
    this.transactions = transactions;
    return this;
  }

  public BlockBodyBuilder ommers(final List<BlockHeader> ommers) {
    this.ommers = ommers;
    return this;
  }

  public BlockBodyBuilder withdrawals(final Optional<List<Withdrawal>> withdrawals) {
    this.withdrawals = withdrawals;
    return this;
  }

  public BlockBodyBuilder deposits(final Optional<List<Deposit>> deposits) {
    this.deposits = deposits;
    return this;
  }

  public BlockBody build() {
    return new BlockBody(transactions, ommers, withdrawals, deposits);
  }
}
