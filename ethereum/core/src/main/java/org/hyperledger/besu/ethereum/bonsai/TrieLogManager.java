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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.Optional;

public interface TrieLogManager {

  void saveTrieLog(
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiWorldStateUpdater localUpdater,
      final Hash worldStateRootHash,
      final BlockHeader blockHeader);

  Optional<MutableWorldState> getBonsaiCachedWorldState(final Hash blockHash);

  long getMaxLayersToLoad();

  void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive);

  void updateCachedLayers(final Hash blockParentHash, final Hash blockHash);

  Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash);

  interface CachedLayer {
    long getHeight();

    TrieLogLayer getTrieLog();

    MutableWorldState getMutableWorldState();
  }
}
