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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public interface PluginLifecycler {
    default List<BesuPlugin> detectPlugins(PluginConfiguration config) {
        ClassLoader pluginLoader =
                pluginDirectoryLoader(config.getPluginsDir()).orElse(getClass().getClassLoader());
        ServiceLoader<BesuPlugin> serviceLoader = ServiceLoader.load(BesuPlugin.class, pluginLoader);
        return StreamSupport.stream(serviceLoader.spliterator(), false).toList();
    }

    void initialize(PluginConfiguration config);

    void registerPlugins();

    void beforeExternalServices();

    void startPlugins();

    void afterExternalServicesMainLoop();

    void stopPlugins();

    Collection<String> getPluginVersions();

    Map<String, BesuPlugin> getNamedPlugins();

    List<String> getPluginsSummaryLog();
}
