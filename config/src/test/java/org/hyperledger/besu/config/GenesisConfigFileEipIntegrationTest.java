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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.EIP;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for loading genesis.json files with EIP configurations. Tests that the complete
 * flow from file -> GenesisConfig -> GenesisConfigOptions correctly parses and exposes EIP
 * information.
 */
public class GenesisConfigFileEipIntegrationTest {

  @Test
  public void testLoadGenesisWithBerlinEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_berlin_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getBerlinBlockNumber()).hasValue(100L);

    List<Integer> berlinEips = options.getEipsForHardfork("berlin");
    assertThat(berlinEips).containsExactly(2565, 2929, 2718, 2930);
  }

  @Test
  public void testLoadGenesisWithLondonEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_london_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getLondonBlockNumber()).hasValue(100L);

    List<Integer> londonEips = options.getEipsForHardfork("london");
    assertThat(londonEips).containsExactly(1559, 3198, 3529);

    Set<EIP> londonEipEnums = options.getEipEnumsForHardfork("london");
    assertThat(londonEipEnums).containsExactlyInAnyOrder(EIP.EIP_1559, EIP.EIP_3198);
  }

  @Test
  public void testLoadGenesisWithShanghaiEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_shanghai_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getShanghaiTime()).hasValue(1000L);

    List<Integer> shanghaiEips = options.getEipsForHardfork("shanghai");
    assertThat(shanghaiEips).containsExactly(3651, 3855, 3860, 4895);
  }

  @Test
  public void testLoadGenesisWithCancunEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_cancun_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getCancunTime()).hasValue(1000L);

    List<Integer> cancunEips = options.getEipsForHardfork("cancun");
    assertThat(cancunEips).containsExactly(4844, 1153, 4788, 5656, 6780, 7516);

    Set<EIP> cancunEipEnums = options.getEipEnumsForHardfork("cancun");
    assertThat(cancunEipEnums).containsExactlyInAnyOrder(EIP.EIP_4844);
  }

  @Test
  public void testLoadGenesisWithPragueEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_prague_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getPragueTime()).hasValue(2000L);

    List<Integer> pragueEips = options.getEipsForHardfork("prague");
    assertThat(pragueEips).containsExactly(6110, 7002, 7251, 7702, 2537, 2935, 7685);

    Set<EIP> pragueEipEnums = options.getEipEnumsForHardfork("prague");
    assertThat(pragueEipEnums)
        .containsExactlyInAnyOrder(EIP.EIP_6110, EIP.EIP_7002, EIP.EIP_7251, EIP.EIP_7702);
  }

  @Test
  public void testLoadGenesisWithOsakaEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_osaka_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getOsakaTime()).hasValue(3000L);

    List<Integer> osakaEips = options.getEipsForHardfork("osaka");
    assertThat(osakaEips).containsExactly(3074, 7692);

    Set<EIP> osakaEipEnums = options.getEipEnumsForHardfork("osaka");
    assertThat(osakaEipEnums).containsExactlyInAnyOrder(EIP.EIP_3074);
  }

  @Test
  public void testLoadGenesisWithAmsterdamEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_amsterdam_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getAmsterdamTime()).hasValue(4000L);

    List<Integer> amsterdamEips = options.getEipsForHardfork("amsterdam");
    assertThat(amsterdamEips).containsExactly(7742);
  }

  @Test
  public void testLoadGenesisWithFutureEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_future_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getFutureEipsTime()).hasValue(4000L);

    List<Integer> futureEips = options.getEipsForHardfork("futureEips");
    assertThat(futureEips).containsExactly(3074, 7702);

    Set<EIP> futureEipEnums = options.getEipEnumsForHardfork("futureEips");
    assertThat(futureEipEnums).containsExactlyInAnyOrder(EIP.EIP_3074, EIP.EIP_7702);
  }

  @Test
  public void testLoadGenesisWithExperimentalEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_experimental_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getExperimentalEipsTime()).hasValue(5000L);

    List<Integer> experimentalEips = options.getEipsForHardfork("experimentalEips");
    assertThat(experimentalEips).containsExactly(3074, 4844, 7702);

    Set<EIP> experimentalEipEnums = options.getEipEnumsForHardfork("experimentalEips");
    assertThat(experimentalEipEnums)
        .containsExactlyInAnyOrder(EIP.EIP_3074, EIP.EIP_4844, EIP.EIP_7702);
  }

  @Test
  public void testLoadGenesisWithMultipleHardforksEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_multiple_hardforks_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Verify London EIPs
    assertThat(options.getLondonBlockNumber()).hasValue(200L);
    List<Integer> londonEips = options.getEipsForHardfork("london");
    assertThat(londonEips).containsExactly(1559, 3198);

    // Verify Cancun EIPs
    assertThat(options.getCancunTime()).hasValue(2000L);
    List<Integer> cancunEips = options.getEipsForHardfork("cancun");
    assertThat(cancunEips).containsExactly(4844, 7516);

    // Verify Prague EIPs
    assertThat(options.getPragueTime()).hasValue(3000L);
    List<Integer> pragueEips = options.getEipsForHardfork("prague");
    assertThat(pragueEips).containsExactly(6110, 7002, 7251, 7702);
  }

  @Test
  public void testLoadGenesisWithNoEips() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_no_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getLondonBlockNumber()).hasValue(100L);

    List<Integer> londonEips = options.getEipsForHardfork("london");
    assertThat(londonEips).isEmpty();

    Set<EIP> londonEipEnums = options.getEipEnumsForHardfork("london");
    assertThat(londonEipEnums).isEmpty();
  }

  @Test
  public void testLoadGenesisWithEmptyEipsArray() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_empty_eips_array.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getLondonBlockNumber()).hasValue(100L);

    List<Integer> londonEips = options.getEipsForHardfork("london");
    assertThat(londonEips).isEmpty();

    Set<EIP> londonEipEnums = options.getEipEnumsForHardfork("london");
    assertThat(londonEipEnums).isEmpty();
  }

  @Test
  public void testAllExistingGenesisFilesStillLoad() {
    // Verify that all existing genesis files still load correctly
    assertThat(GenesisConfig.fromResource("/all_forks.json")).isNotNull();
    assertThat(GenesisConfig.fromResource("/valid_config.json")).isNotNull();
    assertThat(GenesisConfig.fromResource("/valid_config_with_etc_forks.json")).isNotNull();
    assertThat(GenesisConfig.fromResource("/valid_config_with_custom_forks.json")).isNotNull();
    assertThat(GenesisConfig.fromResource("/preMerge.json")).isNotNull();
    assertThat(GenesisConfig.fromResource("/mainnet_with_blob_schedule.json")).isNotNull();
  }

  @Test
  public void testEipQueryingFromLoadedFile() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_prague_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Test case-insensitive hardfork name
    assertThat(options.getEipsForHardfork("PRAGUE"))
        .containsExactly(6110, 7002, 7251, 7702, 2537, 2935, 7685);
    assertThat(options.getEipsForHardfork("Prague"))
        .containsExactly(6110, 7002, 7251, 7702, 2537, 2935, 7685);
    assertThat(options.getEipsForHardfork("prague"))
        .containsExactly(6110, 7002, 7251, 7702, 2537, 2935, 7685);
  }

  @Test
  public void testMissingHardforkReturnsEmptyList() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_london_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Query for a hardfork that doesn't have EIPs configured
    List<Integer> berlinEips = options.getEipsForHardfork("berlin");
    assertThat(berlinEips).isEmpty();

    // Query for a hardfork that doesn't exist in the file
    List<Integer> shanghaEips = options.getEipsForHardfork("shanghai");
    assertThat(shanghaEips).isEmpty();
  }

  @Test
  public void testContractAddressesInPragueGenesis() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_prague_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    assertThat(options.getDepositContractAddress()).isPresent();
    assertThat(options.getWithdrawalRequestContractAddress()).isPresent();
    assertThat(options.getConsolidationRequestContractAddress()).isPresent();
  }

  @Test
  public void testGenesisFileValidationWithEips() {
    // Test that genesis files with EIP configurations are valid and consistent
    GenesisConfig londonConfig = GenesisConfig.fromResource("/genesis_with_london_eips.json");
    assertThat(londonConfig.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(1337));
    assertThat(londonConfig.getDifficulty()).isEqualTo("0x400000");

    GenesisConfig cancunConfig = GenesisConfig.fromResource("/genesis_with_cancun_eips.json");
    assertThat(cancunConfig.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(1337));
    assertThat(cancunConfig.getDifficulty()).isEqualTo("0x0");
  }

  @Test
  public void testBerlinHardforkWithEmptyEipsArray() {
    GenesisConfig config = GenesisConfig.fromResource("/genesis_with_multiple_hardforks_eips.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Berlin has an empty eips array in the test fixture
    List<Integer> berlinEips = options.getEipsForHardfork("berlin");
    assertThat(berlinEips).isEmpty();

    // Shanghai also has an empty eips array
    List<Integer> shanghaiEips = options.getEipsForHardfork("shanghai");
    assertThat(shanghaiEips).isEmpty();
  }

  @Test
  public void testGethStyleGenesisWithoutEipConfiguration() {
    // Verify that Geth-style genesis files (without EIP configuration) still work
    GenesisConfig config = GenesisConfig.fromResource("/geth_style_genesis.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Verify hardfork blocks are parsed correctly
    assertThat(options.getLondonBlockNumber()).hasValue(100L);
    assertThat(options.getShanghaiTime()).hasValue(1000L);
    assertThat(options.getCancunTime()).hasValue(2000L);

    // Verify that querying for EIPs returns empty lists (no EIP configuration)
    assertThat(options.getEipsForHardfork("london")).isEmpty();
    assertThat(options.getEipsForHardfork("shanghai")).isEmpty();
    assertThat(options.getEipsForHardfork("cancun")).isEmpty();

    // Verify the genesis loads successfully
    assertThat(config.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(1337));
  }

  @Test
  public void testAllForksGenesisWithEipConfigurations() {
    // Test that the all_forks.json file includes EIP configurations
    GenesisConfig config = GenesisConfig.fromResource("/all_forks.json");
    GenesisConfigOptions options = config.getConfigOptions();

    // Verify London EIPs are present
    List<Integer> londonEips = options.getEipsForHardfork("london");
    assertThat(londonEips).containsExactly(1559, 3198);

    // Verify Cancun EIPs are present
    List<Integer> cancunEips = options.getEipsForHardfork("cancun");
    assertThat(cancunEips).containsExactly(4844);

    // Verify Prague EIPs are present
    List<Integer> pragueEips = options.getEipsForHardfork("prague");
    assertThat(pragueEips).containsExactly(6110, 7002, 7251, 7702);

    // Verify Osaka EIPs are present
    List<Integer> osakaEips = options.getEipsForHardfork("osaka");
    assertThat(osakaEips).containsExactly(3074);

    // Verify futureEips EIPs are present
    List<Integer> futureEips = options.getEipsForHardfork("futureeips");
    assertThat(futureEips).containsExactly(3074);

    // Verify experimentalEips EIPs are present
    List<Integer> experimentalEips = options.getEipsForHardfork("experimentaleips");
    assertThat(experimentalEips).containsExactly(3074, 7702);
  }
}
