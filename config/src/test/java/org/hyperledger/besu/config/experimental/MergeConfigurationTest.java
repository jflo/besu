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
package org.hyperledger.besu.config.experimental;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Singleton
public class MergeConfigurationTest {

  public MergeConfiguration testConfig;

  @Before
  public void setUp() {
    // every test gets a new, unset config
    this.testConfig = DaggerMergeConfigurationTestComponent.create().mergeConfiguration();
  }

  @Test
  public void shouldBeDisabledByDefault() {
    assertThat(testConfig.isMergeEnabled()).isFalse();
  }

  @Test
  public void shouldDoWithMergeEnabled() {
    testConfig.setMergeEnabled(true);
    final AtomicBoolean check = new AtomicBoolean(false);
    testConfig.doIfMergeEnabled((() -> check.set(true)));
    assertThat(check.get()).isTrue();
  }

  @Test
  public void shouldThrowOnReconfigure() {
    testConfig.setMergeEnabled(true);
    assertThatThrownBy(() -> testConfig.setMergeEnabled(false))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @SuppressWarnings({"JdkObsolete"})
  public void shouldBeEnabledFromCliConsumer() {
    // enable
    var mockStack = new Stack<String>();
    mockStack.push("true");
    MergeConfigurationProvider provider = new MergeConfigurationProvider();
    provider.consumeParameters(mockStack, null, null);
    MergeConfigurationComponent configuredFromCli =
        DaggerMergeConfigurationComponent.builder().mergeConfigurationProvider(provider).build();
    assertThat(configuredFromCli.mergeConfiguration().isMergeEnabled()).isTrue();
  }
}
