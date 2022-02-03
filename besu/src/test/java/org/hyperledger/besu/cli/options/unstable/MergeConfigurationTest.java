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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hyperledger.besu.config.experimental.DaggerMergeConfigurationFactory;
import org.hyperledger.besu.config.experimental.MergeConfiguration;
import org.hyperledger.besu.config.experimental.MergeConfiguration_Factory;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.inject.Inject;

@SuppressWarnings({"JdkObsolete"})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MergeConfigurationTest {

  @Test
  public void shouldBeDisabledByDefault() {
    MergeConfiguration mergeConfiguration = DaggerMergeConfigurationFactory.create().mergeConfiguration();
    assertThat(mergeConfiguration.isMergeEnabled()).isFalse();
  }

  @Test
  public void shouldBeEnabledFromCliConsumer() {
    // enable
    MergeOptions config = DaggerMergeOptionsFactory.create().mergeOptions();
    var mockStack = new Stack<String>();
    mockStack.push("true");
    config.consumeParameters(mockStack, null, null);
    assertThat(config.isMergeEnabled()).isTrue();
    assertThat(config.toDomainObject().isMergeEnabled()).isTrue();
  }

  @Test
  public void shouldDoWithMergeEnabled() {
    MergeConfiguration mergeConfiguration = DaggerMergeConfigurationFactory.create().mergeConfiguration();
    mergeConfiguration.setMergeEnabled(true);
    final AtomicBoolean check = new AtomicBoolean(false);
    mergeConfiguration.doIfMergeEnabled((() -> check.set(true)));
    assertThat(check.get()).isTrue();
  }

  @Test
  public void shouldThrowOnReconfigure() {
    MergeConfiguration mergeConfiguration = DaggerMergeConfigurationFactory.create().mergeConfiguration();
    mergeConfiguration.setMergeEnabled(true);
    assertThatThrownBy(
            () -> mergeConfiguration.setMergeEnabled(false))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void enableConsistentAfterSetting() {
    MergeConfiguration firstMergeConfig = DaggerMergeConfigurationFactory.create().mergeConfiguration();
    firstMergeConfig.setMergeEnabled(true);

    MergeConfiguration secondMergeConfig = DaggerMergeConfigurationFactory.create().mergeConfiguration();;
    assertThat(secondMergeConfig.isMergeEnabled()).isTrue();

  }
}
