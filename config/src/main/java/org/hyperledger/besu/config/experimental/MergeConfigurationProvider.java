/*
 * Copyright Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.config.experimental;

import java.util.List;
import java.util.Stack;

import dagger.Module;
import dagger.Provides;
import picocli.CommandLine;

@Module
public class MergeConfigurationProvider implements CommandLine.IParameterConsumer {
  private MergeConfiguration config = null;
  public boolean MERGE_ENABLED_DEFAULT_VALUE = false;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Enable experimental support for eth1/eth2 merge (default: ${DEFAULT-VALUE})",
      arity = "1",
      parameterConsumer = MergeConfigurationProvider.class)
  @SuppressWarnings({"FieldCanBeFinal"})
  private boolean mergeEnabled = MERGE_ENABLED_DEFAULT_VALUE;

  @Override
  public void consumeParameters(
      final Stack<String> args,
      final CommandLine.Model.ArgSpec argSpec,
      final CommandLine.Model.CommandSpec commandSpec) {
    mergeEnabled = (Boolean.parseBoolean(args.pop()));
  }

  public boolean isMergeEnabled() {
    return this.mergeEnabled;
  }

  public List<String> getCLIOptions() {
    return List.of("--Xmerge-support");
  }

  @Provides
  MergeConfiguration mergeConfiguration() {
    if (this.config == null) {
      this.config = new MergeConfiguration(mergeEnabled);
    }
    return this.config;
  }
}
