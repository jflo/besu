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
package org.hyperledger.besu.ethereum.upgrade.philly;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;

public class PhillyIntegrationTest {

  private MutableBlockchain blockchain;
  private WorldStateArchive worldStateArchive;
  private Block genesisBlock;
  private ProtocolSchedule protocolSchedule;
  private BlockHashLookup blockHashLookup;
    private final BlockDataGenerator gen = new BlockDataGenerator();

    @Before
  public void setUp() {
        //genesis block
        //create and import 1 arrow glacier block at 1.

        final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
        final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
        genesisConfigOptions.arrowGlacierBlock(5L);
        genesisConfigOptions.phillyBlock(10L);
        genesisConfigOptions.chainId(BigInteger.TEN);
        this.protocolSchedule =
              this.createProtocolSchedule(genesisConfigOptions);
        final GenesisState postArrowGlacier = GenesisState.fromConfig(GenesisConfigFile.mainnet(), protocolSchedule);

        this.genesisBlock = postArrowGlacier.getBlock();


        this.blockchain =
                DefaultBlockchain.createMutable(
                        genesisBlock,
                        new KeyValueStoragePrefixedKeyBlockchainStorage(
                                keyValueStorage, new MainnetBlockHeaderFunctions()),
                        new NoOpMetricsSystem(),
                        0);
        this.worldStateArchive = createInMemoryWorldStateArchive();

        //this.protocolContext = new ProtocolContext(blockchain, worldStateArchive, null);
        postArrowGlacier.writeStateTo(worldStateArchive.getMutable());
    generateBlockchainData(9, 2);

    blockHashLookup = new BlockHashLookup(genesisBlock.getHeader(), blockchain);
  }

  @Test
  public void shouldRepriceIntrinsicTxCostOfTransfer() {
      final KeyPair from = SignatureAlgorithmFactory.getInstance().generateKeyPair();
      final KeyPair to = SignatureAlgorithmFactory.getInstance().generateKeyPair();
      generateBlockchainData(1,2); // step into the philly era
    byte[] payload = new byte[10];
    Arrays.fill(payload, (byte)0xad);
    final Transaction transfer =
        Transaction.builder()
                .to(Address.extract(to.getPublicKey()))
                .sender(Address.extract(from.getPublicKey()))
            .type(TransactionType.FRONTIER) //test 1559 txs too
            .gasLimit(21_030)
                //.chainId(BigInteger.TEN)
            .gasPrice(Wei.ONE)
            .nonce(0)
            .payload(Bytes.of(payload))
            .value(Wei.fromEth(1L))
            .signAndBuild(from);

    final BlockHeader genesisBlockHeader = genesisBlock.getHeader();
    final MutableWorldState worldState =
        worldStateArchive
            .getMutable(genesisBlockHeader.getStateRoot(), genesisBlockHeader.getHash())
            .get();
    final WorldUpdater createTransactionUpdater = worldState.updater();
    createTransactionUpdater.createAccount(Address.extract(from.getPublicKey()), 0, Wei.fromEth(2));
    createTransactionUpdater.commit();

    MainnetTransactionProcessor transactionProcessor = protocolSchedule.getByBlockNumber(blockchain.getChainHeadBlockNumber()).getTransactionProcessor();
    TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            blockchain,
            createTransactionUpdater,
            blockchain.getChainHeadHeader(),
            transfer,
            genesisBlockHeader.getCoinbase(),
            blockHashLookup,
            false,
            TransactionValidationParams.blockReplay());
    assertThat(result.isSuccessful()).isTrue();
    assertThat(createTransactionUpdater.getAccount(Address.extract(to.getPublicKey()))).isNotNull();
    assertThat(createTransactionUpdater.getAccount(Address.extract(to.getPublicKey())).getBalance()).isEqualTo(Wei.fromEth(1));
    assertThat(result.getGasRemaining()).isEqualTo(0);

  }

    private ProtocolSchedule createProtocolSchedule(final GenesisConfigOptions genesisConfigOptions) {
        final ProtocolScheduleBuilder protocolScheduleBuilder =
                new ProtocolScheduleBuilder(
                        genesisConfigOptions,
                        BigInteger.ONE,
                        ProtocolSpecAdapters.create(0, Function.identity()),
                        new PrivacyParameters(),
                        false,
                        false,
                        EvmConfiguration.DEFAULT);

        return protocolScheduleBuilder.createProtocolSchedule();
    }

    private void generateBlockchainData(final int numBlocks, final int numAccounts) {
        Block parentBlock = blockchain.getChainHeadBlock();
        for (int i = 0; i < numBlocks; i++) {
            final BlockHeader parentHeader = parentBlock.getHeader();
            final Hash parentHash = parentBlock.getHash();
            final MutableWorldState worldState =
                    worldStateArchive.getMutable(parentHeader.getStateRoot(), parentHash).get();
            gen.createRandomContractAccountsWithNonEmptyStorage(worldState, numAccounts);
            final Hash stateRoot = worldState.rootHash();

            final Block block =
                    gen.block(
                            BlockDataGenerator.BlockOptions.create()
                                    .setStateRoot(stateRoot)
                                    .setBlockNumber(parentHeader.getNumber() + 1L)
                                    .setParentHash(parentHash)
                                    .setBaseFee(Optional.of(1l)));
            final List<TransactionReceipt> receipts = gen.receipts(block);
            blockchain.appendBlock(block, receipts);
            parentBlock = block;
        }
    }
}
