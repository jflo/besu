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
package org.hyperledger.besu.config;

import java.util.concurrent.atomic.AtomicBoolean;

/** The Merge config options. */
// TODO: naming this with Options as the suffix is misleading, it should be MergeConfig - doesn't
// use picocli
public class MergeConfigOptions {
  private static final AtomicBoolean mergeEnabled = new AtomicBoolean(false);

  /** Default constructor. */
  private MergeConfigOptions() {}

  /**
   * Enables merge.
   *
   * @param bool true to enable merge, false otherwise.
   */
  public static void setMergeEnabled(final boolean bool) {
    mergeEnabled.set(bool);
  }

  /**
   * Is merge enabled.
   *
   * @return true if merge is enabled, false otherwise.
   */
  public static boolean isMergeEnabled() {
    return mergeEnabled.get();
  }
}
