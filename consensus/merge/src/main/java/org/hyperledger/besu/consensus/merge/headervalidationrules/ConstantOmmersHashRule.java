/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.consensus.merge.headervalidationrules;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConstantOmmersHashRule implements AttachedBlockHeaderValidationRule {

  private static final Hash mergeConstant =
      Hash.fromHexString("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");

  private static final Logger LOG = LogManager.getLogger(ConstantOmmersHashRule.class);

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {
    Optional<Difficulty> totalDifficulty =
        protocolContext.getBlockchain().getTotalDifficultyByHash(header.getParentHash());
    if (totalDifficulty.isEmpty()) {
      LOG.warn("unable to get total difficulty, parent {} not found", header.getParentHash());
      return false;
    }
    if (totalDifficulty
        .get()
        .greaterOrEqualThan(
            protocolContext.getConsensusContext(MergeContext.class).getTerminalTotalDifficulty())) {
      return header.getOmmersHash().equals(mergeConstant);
    } else {
      return true;
    }
  }

  @Override
  public boolean includeInLightValidation() {
    return AttachedBlockHeaderValidationRule.super.includeInLightValidation();
  }
}
