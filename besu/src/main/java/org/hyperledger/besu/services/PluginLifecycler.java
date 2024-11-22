/*
 *  Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;

import java.util.List;
import java.util.Map;

public interface PluginLifecycler extends PluginVersionsProvider {

  /**
   * Add service. Used by core besu or other plugins to add services to the service manager.
   *
   * @param <T> the type parameter
   * @param serviceType the service type
   * @param service the service
   */
  <T extends BesuService> void addService(final Class<T> serviceType, final T service);

  List<BesuPlugin> detectPlugins(final PluginConfiguration config);

  void initialize(final PluginConfiguration config);

  void registerPlugins();

  void beforeExternalServices();

  void startPlugins();

  void afterExternalServicesMainLoop();

  void stopPlugins();

  Map<String, BesuPlugin> getNamedPlugins();

  List<String> getPluginsSummaryLog();
}
