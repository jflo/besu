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
package org.hyperledger.besu.consensus.merge.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.experimental.MergeOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardsSyncContext;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeReorgTest implements MergeGenesisConfigHelper {

  @Mock AbstractPendingTransactionsSorter mockSorter;

  private MergeCoordinator coordinator;

  private static final long DIFFICULTY_LEFT = 1000;
  private final MergeContext mergeContext = PostMergeContext.get();
  private final ProtocolSchedule mockProtocolSchedule = getMergeProtocolSchedule();
  private final GenesisState genesisState =
      GenesisState.fromConfig(getGenesisConfigFile(), mockProtocolSchedule);

  private final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());

  private final ProtocolContext protocolContext =
      new ProtocolContext(blockchain, worldStateArchive, mergeContext);

  private final Address coinbase = genesisAllocations().findFirst().get();
  private final BlockHeaderTestFixture headerGenerator = new BlockHeaderTestFixture();
  private final BaseFeeMarket feeMarket =
      new LondonFeeMarket(0, genesisState.getBlock().getHeader().getBaseFee());

  @Before
  public void setUp() {
    var mutable = worldStateArchive.getMutable();
    genesisState.writeStateTo(mutable);
    mutable.persist(null);

    MergeOptions.setMergeEnabled(true);
    this.coordinator =
        new MergeCoordinator(
            protocolContext,
            mockProtocolSchedule,
            mockSorter,
            new MiningParameters.Builder().coinbase(coinbase).build(),
            mock(BackwardsSyncContext.class));
    mergeContext.setTerminalTotalDifficulty(
        genesisState.getBlock().getHeader().getDifficulty().plus(DIFFICULTY_LEFT));
    mergeContext.setIsPostMerge(genesisState.getBlock().getHeader().getDifficulty());
  }

  /* as long as a post-merge PoS block has not been finalized,
  then yuo can and should be able to re-org to a different pre-TTD block
  say there is viable TTD block A and B, then we can have a PoS chain build on A for a while
      and then see another PoS chain build on B that has a higher fork choice weight and causes a re-org
  once any post-merge PoS chain is finalied though, you'd never re-org any PoW blocks in the tree ever again */

  @Test
  public void reorgsAcrossTDDToDifferentTargetsWhenNotFinal() {
    // Add N blocks to chain from genesis, where total diff is < TTD
    List<Block> endOfWork = subChain(genesisState.getBlock().getHeader(), 10, Difficulty.of(100L));
    endOfWork.stream().forEach(coordinator::executeBlock);
    assertThat(blockchain.getChainHead().getHeight()).isEqualTo(10L);
    BlockHeader tddPenultimate = this.blockchain.getChainHeadHeader();
    // Add TDD block A to chain as child of N.
    Block tddA = new Block(terminalPowBlock(tddPenultimate), BlockBody.empty());
    boolean worked = coordinator.executeBlock(tddA);
    assertThat(worked).isTrue();
    assertThat(blockchain.getChainHead().getHeight()).isEqualTo(11L);
    assertThat(blockchain.getTotalDifficultyByHash(tddA.getHash())).isPresent();
    Difficulty tdd = blockchain.getTotalDifficultyByHash(tddA.getHash()).get();
    assertThat(tdd.getAsBigInteger()).isGreaterThan(BigInteger.valueOf(1000));
    assertThat(mergeContext.isPostMerge()).isTrue();
    // don't finalize
    // Create a new chain back to A which has

  }

  private List<Block> subChain(
      final BlockHeader parentHeader, final long length, final Difficulty each) {
    BlockHeader newParent = parentHeader;
    List<Block> retval = new ArrayList<>();
    for (int i = 1; i <= length; i++) {
      BlockHeader h =
          headerGenerator
              .difficulty(each)
              .parentHash(newParent.getHash())
              .number(newParent.getNumber() + 1)
              .baseFeePerGas(
                  feeMarket.computeBaseFee(
                      genesisState.getBlock().getHeader().getNumber() + 1,
                      newParent.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                      0,
                      15000000))
              .gasLimit(newParent.getGasLimit())
              .stateRoot(newParent.getStateRoot())
              .buildHeader();
      retval.add(new Block(h, BlockBody.empty()));
      newParent = h;
    }
    return retval;
  }

  private BlockHeader terminalPowBlock(final BlockHeader parent) {

    BlockHeader terminal =
        headerGenerator
            .difficulty(Difficulty.of(10000))
            .parentHash(parent.getHash())
            .number(parent.getNumber() + 1)
            .baseFeePerGas(
                feeMarket.computeBaseFee(
                    genesisState.getBlock().getHeader().getNumber() + 1,
                    parent.getBaseFee().orElse(Wei.of(0x3b9aca00)),
                    0,
                    15000000l))
            .gasLimit(parent.getGasLimit())
            .stateRoot(parent.getStateRoot())
            .buildHeader();
    // mergeContext.setTerminalPoWBlock(Optional.of(terminal));
    return terminal;
  }
}
