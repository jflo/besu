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

package org.hyperledger.besu.evm.internal;

import java.nio.file.Path;
import java.util.Optional;

public class EvmConfiguration {
  public static final EvmConfiguration DEFAULT = new EvmConfiguration(32_000L, Optional.empty());
  private final long jumpDestCacheWeightKB;

  private final Optional<Path> kzgTrustedSetupPath;

  public EvmConfiguration(
      final long jumpDestCacheWeightKB, final Optional<Path> pathToKZGTrustedSetup) {
    this.jumpDestCacheWeightKB = jumpDestCacheWeightKB;
    this.kzgTrustedSetupPath = pathToKZGTrustedSetup;
  }

  /**
   * Gets jump dest cache weight bytes.
   *
   * @return the jump dest cache weight bytes
   */
  public long getJumpDestCacheWeightBytes() {
    return jumpDestCacheWeightKB * 1024L;
  }

  public Optional<Path> getKzgTrustedSetupPath() {
    return this.kzgTrustedSetupPath;
  }
}
