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

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

public class GenesisConfigEipsTest {

  @Test
  public void testParseEipsFromGenesis() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0,
            "london": {
              "eips": [1559, 3198, 3529]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> londonEips = config.getEipsForHardfork("london");
    assertThat(londonEips).containsExactly(1559, 3198, 3529);
  }

  @Test
  public void testMissingEipsReturnsEmpty() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> londonEips = config.getEipsForHardfork("london");
    assertThat(londonEips).isEmpty();
  }

  @Test
  public void testMissingHardforkReturnsEmpty() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> cancunEips = config.getEipsForHardfork("cancun");
    assertThat(cancunEips).isEmpty();
  }

  @Test
  public void testMultipleHardforksWithEips() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0,
            "london": {
              "eips": [1559]
            },
            "cancunTime": 1000,
            "cancun": {
              "eips": [4844, 1153]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    assertThat(config.getEipsForHardfork("london")).containsExactly(1559);
    assertThat(config.getEipsForHardfork("cancun")).containsExactly(4844, 1153);
  }

  @Test
  public void testEipEnumsForHardfork() {
    String genesis =
        """
        {
          "config": {
            "pragueTime": 0,
            "prague": {
              "eips": [6110, 7002, 7251, 7702]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    Set<EIP> pragueEips = config.getEipEnumsForHardfork("prague");
    assertThat(pragueEips)
        .containsExactlyInAnyOrder(EIP.EIP_6110, EIP.EIP_7002, EIP.EIP_7251, EIP.EIP_7702);
  }

  @Test
  public void testEipEnumsWithUnknownEips() {
    String genesis =
        """
        {
          "config": {
            "pragueTime": 0,
            "prague": {
              "eips": [6110, 9999, 7702]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    // Unknown EIP 9999 should be filtered out
    Set<EIP> pragueEips = config.getEipEnumsForHardfork("prague");
    assertThat(pragueEips).containsExactlyInAnyOrder(EIP.EIP_6110, EIP.EIP_7702);
  }

  @Test
  public void testFutureEipsHardfork() {
    String genesis =
        """
        {
          "config": {
            "futureEipsTime": 0,
            "futureeips": {
              "eips": [3074]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> futureEips = config.getEipsForHardfork("futureEips");
    assertThat(futureEips).containsExactly(3074);
  }

  @Test
  public void testExperimentalEipsHardfork() {
    String genesis =
        """
        {
          "config": {
            "experimentalEipsTime": 0,
            "experimentaleips": {
              "eips": [3074, 4844]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> experimentalEips = config.getEipsForHardfork("experimentalEips");
    assertThat(experimentalEips).containsExactly(3074, 4844);
  }

  @Test
  public void testCaseInsensitiveHardforkNames() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0,
            "london": {
              "eips": [1559]
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    // Both lowercase and mixed case should work
    assertThat(config.getEipsForHardfork("london")).containsExactly(1559);
    assertThat(config.getEipsForHardfork("LONDON")).containsExactly(1559);
    assertThat(config.getEipsForHardfork("London")).containsExactly(1559);
  }

  @Test
  public void testEmptyEipsArray() {
    String genesis =
        """
        {
          "config": {
            "londonBlock": 0,
            "london": {
              "eips": []
            }
          }
        }
        """;

    ObjectNode configNode = JsonUtil.objectNodeFromString(genesis);
    GenesisConfigOptions config =
        JsonGenesisConfigOptions.fromJsonObject((ObjectNode) configNode.get("config"));

    List<Integer> londonEips = config.getEipsForHardfork("london");
    assertThat(londonEips).isEmpty();
  }
}
