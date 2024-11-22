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
package org.hyperledger.besu.components;

import org.hyperledger.besu.plugin.ServiceProvider;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.PluginLifecycler;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class NoOpPluginsModule {

  /**
   * Creates a BesuPluginContextImpl, used for plugin service discovery.
   *
   * @return the BesuPluginContext
   */
  @Provides
  @Singleton
  public static BesuPluginContextImpl provideBesuPluginContext() {
    BesuPluginContextImpl retval = new BesuPluginContextImpl();
    retval.addService(BesuConfiguration.class, new BesuConfigurationImpl());
    return retval;
  }

  @Binds
  abstract ServiceProvider bindBesuContext(BesuPluginContextImpl besuPluginContext);

  @Binds
  abstract PluginLifecycler bindPluginLifecycler(BesuPluginContextImpl besuPluginContext);
}
