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

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.datatypes.EIP;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import org.junit.jupiter.api.Test;

/**
 * Integration test for end-to-end EIP configuration flow. Tests that EIPs configured in
 * genesis.json are correctly propagated through the protocol schedule and can be queried from
 * protocol specs.
 */
public class ProtocolScheduleEipIntegrationTest {

  @Test
  public void testProtocolSpecHasConfiguredEips() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "cancunTime": 0,
            "cancun": {
              "eips": [4844, 7516]
            }
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    // Get spec for a block after Cancun activation
    ProtocolSpec cancunSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    assertThat(cancunSpec.activates(EIP.EIP_4844)).isTrue();
    assertThat(cancunSpec.activates(EIP.EIP_3074)).isFalse();
  }

  @Test
  public void testMultipleHardforksWithDifferentEips() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "londonBlock": 100,
            "london": {
              "eips": [1559, 3198]
            },
            "cancunTime": 1000,
            "cancun": {
              "eips": [4844, 1153]
            }
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    // London spec should have London EIPs
    ProtocolSpec londonSpec = schedule.getByBlockHeader(createBlockHeader(100, 0));
    assertThat(londonSpec.activates(EIP.EIP_1559)).isTrue();
    assertThat(londonSpec.activates(EIP.EIP_3198)).isTrue();
    assertThat(londonSpec.activates(EIP.EIP_4844)).isFalse();

    // Cancun spec should have Cancun EIPs
    ProtocolSpec cancunSpec = schedule.getByBlockHeader(createBlockHeader(200, 1000));
    assertThat(cancunSpec.activates(EIP.EIP_4844)).isTrue();
    assertThat(cancunSpec.activates(EIP.EIP_1559)).isFalse();
    assertThat(cancunSpec.activates(EIP.EIP_3198)).isFalse();
  }

  @Test
  public void testPragueEips() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "pragueTime": 0,
            "prague": {
              "eips": [6110, 7002, 7251, 7702]
            },
            "depositContractAddress": "0x00000000219ab540356cbb839cbe05303d7705fa",
            "withdrawalRequestContractAddress": "0x0c15f14308530b7cdb8460094daf9dd1e549d54d",
            "consolidationRequestContractAddress": "0x01aBEa29659e5e97C95107F20bb753cD3e09bBBb"
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec pragueSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    assertThat(pragueSpec.activates(EIP.EIP_6110)).isTrue();
    assertThat(pragueSpec.activates(EIP.EIP_7002)).isTrue();
    assertThat(pragueSpec.activates(EIP.EIP_7251)).isTrue();
    assertThat(pragueSpec.activates(EIP.EIP_7702)).isTrue();
    assertThat(pragueSpec.getActivatedEIPs()).hasSize(4);
  }

  @Test
  public void testOsakaEips() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "osakaTime": 0,
            "osaka": {
              "eips": [3074]
            },
            "depositContractAddress": "0x00000000219ab540356cbb839cbe05303d7705fa",
            "withdrawalRequestContractAddress": "0x0c15f14308530b7cdb8460094daf9dd1e549d54d",
            "consolidationRequestContractAddress": "0x01aBEa29659e5e97C95107F20bb753cD3e09bBBb"
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec osakaSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    // Osaka should have its configured EIPs
    assertThat(osakaSpec.activates(EIP.EIP_3074)).isTrue();
    assertThat(osakaSpec.getActivatedEIPs()).hasSize(1);
  }

  @Test
  public void testFutureEipsHardfork() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "futureEipsTime": 0,
            "futureeips": {
              "eips": [3074]
            },
            "depositContractAddress": "0x00000000219ab540356cbb839cbe05303d7705fa",
            "withdrawalRequestContractAddress": "0x0c15f14308530b7cdb8460094daf9dd1e549d54d",
            "consolidationRequestContractAddress": "0x01aBEa29659e5e97C95107F20bb753cD3e09bBBb"
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec futureSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    assertThat(futureSpec.activates(EIP.EIP_3074)).isTrue();
  }

  @Test
  public void testExperimentalEipsHardfork() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "experimentalEipsTime": 0,
            "experimentaleips": {
              "eips": [3074, 7702]
            },
            "depositContractAddress": "0x00000000219ab540356cbb839cbe05303d7705fa",
            "withdrawalRequestContractAddress": "0x0c15f14308530b7cdb8460094daf9dd1e549d54d",
            "consolidationRequestContractAddress": "0x01aBEa29659e5e97C95107F20bb753cD3e09bBBb"
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec experimentalSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    assertThat(experimentalSpec.activates(EIP.EIP_3074)).isTrue();
    assertThat(experimentalSpec.activates(EIP.EIP_7702)).isTrue();
  }

  @Test
  public void testNoEipsConfigured() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "londonBlock": 0
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec londonSpec = schedule.getByBlockHeader(createBlockHeader(1, 0));

    // Should have empty EIP set when no EIPs are configured
    assertThat(londonSpec.getActivatedEIPs()).isEmpty();
    assertThat(londonSpec.activates(EIP.EIP_1559)).isFalse();
  }

  @Test
  public void testEmptyEipsArray() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "londonBlock": 0,
            "london": {
              "eips": []
            }
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec londonSpec = schedule.getByBlockHeader(createBlockHeader(1, 0));

    assertThat(londonSpec.getActivatedEIPs()).isEmpty();
  }

  @Test
  public void testUnknownEipsAreFiltered() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "pragueTime": 0,
            "prague": {
              "eips": [6110, 99999, 7702, 88888]
            },
            "depositContractAddress": "0x00000000219ab540356cbb839cbe05303d7705fa",
            "withdrawalRequestContractAddress": "0x0c15f14308530b7cdb8460094daf9dd1e549d54d",
            "consolidationRequestContractAddress": "0x01aBEa29659e5e97C95107F20bb753cD3e09bBBb"
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    ProtocolSpec pragueSpec = schedule.getByBlockHeader(createBlockHeader(1, 1000));

    // Only known EIPs should be present
    assertThat(pragueSpec.activates(EIP.EIP_6110)).isTrue();
    assertThat(pragueSpec.activates(EIP.EIP_7702)).isTrue();
    // Unknown EIPs 99999 and 88888 should be filtered out
    assertThat(pragueSpec.getActivatedEIPs()).hasSize(2);
  }

  @Test
  public void testEipQueryingAcrossHardforkTransitions() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "londonBlock": 100,
            "london": {
              "eips": [1559]
            },
            "cancunTime": 1000,
            "cancun": {
              "eips": [4844]
            }
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    // Before London
    ProtocolSpec frontierSpec = schedule.getByBlockHeader(createBlockHeader(50, 0));
    assertThat(frontierSpec.getActivatedEIPs()).isEmpty();

    // At London
    ProtocolSpec londonSpec = schedule.getByBlockHeader(createBlockHeader(100, 0));
    assertThat(londonSpec.activates(EIP.EIP_1559)).isTrue();
    assertThat(londonSpec.activates(EIP.EIP_4844)).isFalse();

    // Between London and Cancun
    ProtocolSpec betweenSpec = schedule.getByBlockHeader(createBlockHeader(150, 500));
    assertThat(betweenSpec.activates(EIP.EIP_1559)).isTrue();
    assertThat(betweenSpec.activates(EIP.EIP_4844)).isFalse();

    // At Cancun
    ProtocolSpec cancunSpec = schedule.getByBlockHeader(createBlockHeader(200, 1000));
    assertThat(cancunSpec.activates(EIP.EIP_4844)).isTrue();
    assertThat(cancunSpec.activates(EIP.EIP_1559)).isFalse();
  }

  @Test
  public void testEipAccumulationAcrossHardforks() {
    String genesis =
        """
        {
          "config": {
            "chainId": 1337,
            "berlinBlock": 50,
            "berlin": {
              "eips": [3074, 7002]
            },
            "londonBlock": 100,
            "london": {
              "eips": [1559, 3198]
            },
            "cancunTime": 1000,
            "cancun": {
              "eips": [4844, 7251]
            }
          }
        }
        """;

    ProtocolSchedule schedule = createSchedule(genesis);

    // Berlin spec: EIPs 3074, 7002 activated
    ProtocolSpec berlinSpec = schedule.getByBlockHeader(createBlockHeader(50, 0));
    assertThat(berlinSpec.activates(EIP.EIP_3074)).isTrue();
    assertThat(berlinSpec.activates(EIP.EIP_7002)).isTrue();
    assertThat(berlinSpec.isActive(EIP.EIP_3074)).isTrue();
    assertThat(berlinSpec.isActive(EIP.EIP_7002)).isTrue();
    assertThat(berlinSpec.getActivatedEIPs()).containsExactlyInAnyOrder(EIP.EIP_3074, EIP.EIP_7002);
    assertThat(berlinSpec.getActiveEIPs()).containsExactlyInAnyOrder(EIP.EIP_3074, EIP.EIP_7002);

    // London spec: EIPs 1559, 3198 activated; 3074, 7002 still active (accumulated)
    ProtocolSpec londonSpec = schedule.getByBlockHeader(createBlockHeader(100, 0));
    // Activated in London
    assertThat(londonSpec.activates(EIP.EIP_1559)).isTrue();
    assertThat(londonSpec.activates(EIP.EIP_3198)).isTrue();
    assertThat(londonSpec.activates(EIP.EIP_3074)).isFalse(); // Not activated in London
    assertThat(londonSpec.activates(EIP.EIP_7002)).isFalse(); // Not activated in London

    // Active (accumulated from Berlin + London)
    assertThat(londonSpec.isActive(EIP.EIP_1559)).isTrue();
    assertThat(londonSpec.isActive(EIP.EIP_3198)).isTrue();
    assertThat(londonSpec.isActive(EIP.EIP_3074)).isTrue(); // From Berlin
    assertThat(londonSpec.isActive(EIP.EIP_7002)).isTrue(); // From Berlin

    assertThat(londonSpec.getActivatedEIPs()).containsExactlyInAnyOrder(EIP.EIP_1559, EIP.EIP_3198);
    assertThat(londonSpec.getActiveEIPs())
        .containsExactlyInAnyOrder(
            EIP.EIP_3074, EIP.EIP_7002, EIP.EIP_1559, EIP.EIP_3198); // Accumulated

    // Cancun spec: EIPs 4844, 7251 activated; all prior EIPs still active
    ProtocolSpec cancunSpec = schedule.getByBlockHeader(createBlockHeader(200, 1000));
    // Activated in Cancun
    assertThat(cancunSpec.activates(EIP.EIP_4844)).isTrue();
    assertThat(cancunSpec.activates(EIP.EIP_7251)).isTrue();
    assertThat(cancunSpec.activates(EIP.EIP_1559)).isFalse(); // Not activated in Cancun
    assertThat(cancunSpec.activates(EIP.EIP_3074)).isFalse(); // Not activated in Cancun

    // Active (accumulated from Berlin + London + Cancun)
    assertThat(cancunSpec.isActive(EIP.EIP_4844)).isTrue();
    assertThat(cancunSpec.isActive(EIP.EIP_7251)).isTrue();
    assertThat(cancunSpec.isActive(EIP.EIP_1559)).isTrue(); // From London
    assertThat(cancunSpec.isActive(EIP.EIP_3198)).isTrue(); // From London
    assertThat(cancunSpec.isActive(EIP.EIP_3074)).isTrue(); // From Berlin
    assertThat(cancunSpec.isActive(EIP.EIP_7002)).isTrue(); // From Berlin

    assertThat(cancunSpec.getActivatedEIPs()).containsExactlyInAnyOrder(EIP.EIP_4844, EIP.EIP_7251);
    assertThat(cancunSpec.getActiveEIPs())
        .containsExactlyInAnyOrder(
            EIP.EIP_3074,
            EIP.EIP_7002,
            EIP.EIP_1559,
            EIP.EIP_3198,
            EIP.EIP_4844,
            EIP.EIP_7251); // All accumulated
  }

  private ProtocolSchedule createSchedule(final String genesisJson) {
    return MainnetProtocolSchedule.fromConfig(
        GenesisConfig.fromConfig(genesisJson).getConfigOptions(),
        EvmConfiguration.DEFAULT,
        MiningConfiguration.MINING_DISABLED,
        new BadBlockManager(),
        false,
        BalConfiguration.DEFAULT,
        new NoOpMetricsSystem());
  }

  private BlockHeader createBlockHeader(final long blockNumber, final long timestamp) {
    return new BlockHeaderTestFixture().number(blockNumber).timestamp(timestamp).buildHeader();
  }
}
