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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.LONDON;

import org.hyperledger.besu.datatypes.EIP;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.staterootcommitter.StateRootCommitterFactoryDefault;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class ProtocolSpecEipQueryTest {

  @Test
  public void testIsEipEnabled() {
    ProtocolSpec spec = createSpecWithEips(Set.of(EIP.EIP_1559, EIP.EIP_4844));

    assertThat(spec.activates(EIP.EIP_1559)).isTrue();
    assertThat(spec.activates(EIP.EIP_4844)).isTrue();
    assertThat(spec.activates(EIP.EIP_3074)).isFalse();
  }

  @Test
  public void testGetActivatedEIPs() {
    Set<EIP> eips = Set.of(EIP.EIP_1559, EIP.EIP_3198);
    ProtocolSpec spec = createSpecWithEips(eips);

    assertThat(spec.getActivatedEIPs()).isEqualTo(eips);
  }

  @Test
  public void testEmptyEips() {
    ProtocolSpec spec = createSpecWithEips(Collections.emptySet());

    assertThat(spec.activates(EIP.EIP_1559)).isFalse();
    assertThat(spec.getActivatedEIPs()).isEmpty();
  }

  @Test
  public void testEipImmutability() {
    Set<EIP> eips = Set.of(EIP.EIP_1559);
    ProtocolSpec spec = createSpecWithEips(eips);

    // Verify that the returned set is immutable by attempting to modify it
    Set<EIP> returnedEips = spec.getActivatedEIPs();
    assertThat(returnedEips).isEqualTo(eips);
    assertThat(returnedEips).isUnmodifiable();
  }

  @Test
  public void testEipFromNumber() {
    assertThat(EIP.fromNumber(1559)).isEqualTo(Optional.of(EIP.EIP_1559));
    assertThat(EIP.fromNumber(4844)).isEqualTo(Optional.of(EIP.EIP_4844));
    assertThat(EIP.fromNumber(3074)).isEqualTo(Optional.of(EIP.EIP_3074));
    assertThat(EIP.fromNumber(9999)).isEqualTo(Optional.empty());
  }

  @Test
  public void testEipGetNumber() {
    assertThat(EIP.EIP_1559.getNumber()).isEqualTo(1559);
    assertThat(EIP.EIP_4844.getNumber()).isEqualTo(4844);
    assertThat(EIP.EIP_7702.getNumber()).isEqualTo(7702);
  }

  @Test
  public void testMultipleEips() {
    Set<EIP> eips =
        Set.of(
            EIP.EIP_1559,
            EIP.EIP_3074,
            EIP.EIP_3198,
            EIP.EIP_4844,
            EIP.EIP_6110,
            EIP.EIP_7002,
            EIP.EIP_7251,
            EIP.EIP_7702);
    ProtocolSpec spec = createSpecWithEips(eips);

    // Verify all configured EIPs are activated
    for (EIP eip : eips) {
      assertThat(spec.activates(eip)).isTrue();
    }

    // Verify the set size matches
    assertThat(spec.getActivatedEIPs()).hasSize(8);
  }

  @Test
  public void testActivatesVsIsActive() {
    // Test the distinction between activated (this hardfork) and active (accumulated)
    Set<EIP> activatedEips = Set.of(EIP.EIP_1559, EIP.EIP_3198);
    Set<EIP> allActiveEips =
        Set.of(EIP.EIP_3074, EIP.EIP_7002, EIP.EIP_1559, EIP.EIP_3198); // Includes prior EIPs
    ProtocolSpec spec = createSpecWithEips(activatedEips, allActiveEips);

    // EIPs activated in this hardfork
    assertThat(spec.activates(EIP.EIP_1559)).isTrue();
    assertThat(spec.activates(EIP.EIP_3198)).isTrue();
    assertThat(spec.activates(EIP.EIP_3074)).isFalse(); // From prior hardfork
    assertThat(spec.activates(EIP.EIP_7002)).isFalse(); // From prior hardfork

    // All active EIPs (accumulated)
    assertThat(spec.isActive(EIP.EIP_1559)).isTrue();
    assertThat(spec.isActive(EIP.EIP_3198)).isTrue();
    assertThat(spec.isActive(EIP.EIP_3074)).isTrue(); // From prior hardfork
    assertThat(spec.isActive(EIP.EIP_7002)).isTrue(); // From prior hardfork
    assertThat(spec.isActive(EIP.EIP_4844)).isFalse(); // Future EIP
  }

  @Test
  public void testGetActiveEIPs() {
    Set<EIP> activatedEips = Set.of(EIP.EIP_1559);
    Set<EIP> allActiveEips = Set.of(EIP.EIP_3074, EIP.EIP_1559, EIP.EIP_3198);
    ProtocolSpec spec = createSpecWithEips(activatedEips, allActiveEips);

    assertThat(spec.getActivatedEIPs()).isEqualTo(activatedEips);
    assertThat(spec.getActiveEIPs()).isEqualTo(allActiveEips);
  }

  @Test
  public void testImmutabilityOfBothSets() {
    Set<EIP> activatedEips = Set.of(EIP.EIP_1559);
    Set<EIP> allActiveEips = Set.of(EIP.EIP_3074, EIP.EIP_1559);
    ProtocolSpec spec = createSpecWithEips(activatedEips, allActiveEips);

    assertThat(spec.getActivatedEIPs()).isUnmodifiable();
    assertThat(spec.getActiveEIPs()).isUnmodifiable();
  }

  private ProtocolSpec createSpecWithEips(final Set<EIP> eips) {
    // For backward compatibility, when only one set is provided, use it for both
    return createSpecWithEips(eips, eips);
  }

  private ProtocolSpec createSpecWithEips(
      final Set<EIP> activatedEips, final Set<EIP> allActiveEips) {
    // Create a minimal ProtocolSpec with the given EIPs
    return new ProtocolSpec(
        LONDON,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Wei.ZERO,
        null,
        new PrecompileContractRegistry(),
        false,
        null,
        null,
        FeeMarket.legacy(),
        Optional.empty(),
        null,
        Optional.empty(),
        null,
        Optional.empty(),
        null,
        false,
        Duration.ofSeconds(12),
        false,
        Optional.empty(),
        Optional.empty(),
        new StateRootCommitterFactoryDefault(),
        activatedEips,
        allActiveEips);
  }
}
