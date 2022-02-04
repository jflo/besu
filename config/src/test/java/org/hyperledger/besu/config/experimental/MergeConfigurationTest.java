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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.inject.Inject;
import javax.inject.Singleton;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Singleton
public class MergeConfigurationTest {

  public MergeConfigurationTestComponent providesConfig;
  public MergeConfiguration providedConfig;

  @Before
  public void setUp() {
    this.providesConfig = DaggerMergeConfigurationTestComponent.builder().build();
    this.providedConfig = providesConfig.mergeConfiguration();
  }

  @Test
  public void shouldBeDisabledByDefault() {
    assertThat(providedConfig.isMergeEnabled()).isFalse();
  }

  @Test
  public void shouldDoWithMergeEnabled() {
    providedConfig.setMergeEnabled(true);
    final AtomicBoolean check = new AtomicBoolean(false);
    providedConfig.doIfMergeEnabled((() -> check.set(true)));
    assertThat(check.get()).isTrue();
  }

  @Test
  public void shouldThrowOnReconfigure() {
    providedConfig.setMergeEnabled(true);
    assertThatThrownBy(
            () -> providedConfig.setMergeEnabled(false))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void enableConsistentAfterSetting() {
    providedConfig.setMergeEnabled(true);

    providedConfig = providesConfig.mergeConfiguration();
    assertThat(providedConfig.isMergeEnabled()).isTrue();

  }
}
