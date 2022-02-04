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
package org.hyperledger.besu.cli.options.unstable;

import dagger.Provides;
import dagger.Module;
import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.config.experimental.DaggerMergeConfigurationComponent;
import org.hyperledger.besu.config.experimental.MergeConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Stack;



/** Unstable support for eth1/2 merge */
public class MergeOptions implements CLIOptions<MergeConfiguration>, CommandLine.IParameterConsumer {
  // To make it easier for tests to reset the value to default
  public boolean MERGE_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Enable experimental support for eth1/eth2 merge (default: ${DEFAULT-VALUE})",
      arity = "1",
      parameterConsumer = MergeOptions.class)
  @SuppressWarnings({"FieldCanBeFinal"})
  private boolean mergeEnabled = MERGE_ENABLED_DEFAULT_VALUE;

  @Inject
  public MergeOptions() {

  }

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

  @Override
  public MergeConfiguration toDomainObject() {
    MergeConfiguration config = DaggerMergeConfigurationComponent.create().mergeConfiguration();
    config.setMergeEnabled(this.mergeEnabled);
    return config;
  }

  @Override
  public List<String> getCLIOptions() {
    return List.of("--Xmerge-support");
  }
}
