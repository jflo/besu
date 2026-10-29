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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.PicoCLIOptions;

import java.util.Map;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/** The Pico cli options service implementation to specify plugins. */
public class PicoCLIOptionsImpl implements PicoCLIOptions {

  private static final Logger LOG = LoggerFactory.getLogger(PicoCLIOptionsImpl.class);

  /**
   * Option name prefixes that a plugin bundled with Besu, registering its options under a
   * namespace, may use besides {@code --plugin-<namespace>-}, so that a feature moved out of Besu
   * into a bundled plugin keeps the option names it had in Besu. Plugins installed in the plugins
   * directory may not use them.
   *
   * <p>DEPRECATED: GraphQL moved into the besu-graphql-plugin, which Besu bundles. These legacy
   * {@code --graphql-} names are deprecated and will be removed, together with the bundling of the
   * plugin, in a future release (see the CHANGELOG).
   */
  static final Map<String, String> LEGACY_OPTION_PREFIXES = Map.of("graphql", "--graphql-");

  private final CommandLine commandLine;
  private final BooleanSupplier legacyPrefixesAllowed;

  /**
   * Instantiates a new Pico cli options, which accepts only the {@code --plugin-<namespace>-} and
   * {@code --Xplugin-<namespace>-} prefixes.
   *
   * @param commandLine the command line
   */
  public PicoCLIOptionsImpl(final CommandLine commandLine) {
    this(commandLine, () -> false);
  }

  /**
   * Instantiates a new Pico cli options.
   *
   * @param commandLine the command line
   * @param legacyPrefixesAllowed whether the plugin adding options may use the {@link
   *     #LEGACY_OPTION_PREFIXES}: true while a plugin bundled with Besu is registering
   */
  public PicoCLIOptionsImpl(
      final CommandLine commandLine, final BooleanSupplier legacyPrefixesAllowed) {
    this.commandLine = commandLine;
    this.legacyPrefixesAllowed = legacyPrefixesAllowed;
  }

  @Override
  public void addPicoCLIOptions(final String namespace, final Object optionObject) {
    final String pluginPrefix = "--plugin-" + namespace + "-";
    final String unstablePrefix = "--Xplugin-" + namespace + "-";
    final String legacyPrefix =
        legacyPrefixesAllowed.getAsBoolean() ? LEGACY_OPTION_PREFIXES.get(namespace) : null;
    final CommandSpec mixin = CommandSpec.forAnnotatedObject(optionObject);
    boolean badOptionName = false;

    for (final OptionSpec optionSpec : mixin.options()) {
      for (final String optionName : optionSpec.names()) {
        if (!optionName.startsWith(pluginPrefix)
            && !optionName.startsWith(unstablePrefix)
            && (legacyPrefix == null || !optionName.startsWith(legacyPrefix))) {
          badOptionName = true;
          LOG.error(
              "Plugin option {} did not have the expected prefix of {}", optionName, pluginPrefix);
        }
      }
    }
    if (badOptionName) {
      throw new RuntimeException("Error loading CLI options");
    } else {
      commandLine.getCommandSpec().addMixin("Plugin " + namespace, mixin);
    }
  }
}
