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
package org.hyperledger.besu.components;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.options.stable.P2PDiscoveryOptionGroup;
import org.hyperledger.besu.cli.options.unstable.ChainPruningOptions;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.FrontierTargetingGasLimitCalculator;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;

import javax.inject.Named;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

@Module
public class BesuControllerModule {

    @Provides
    public BesuController provideBesuController(final EthNetworkConfig ethNetworkConfig,
                                                final SyncMode syncMode,
                                                final SynchronizerConfiguration synchronizerConfiguration,
                                                final EthProtocolConfiguration ethProtocolConfiguration,
                                                final NetworkingConfiguration networkingConfiguration,
                                                final @Named("dataDir") Path dataDir,
                                                final DataStorageConfiguration dataStorageConfiguration,
                                                final MiningParameters miningParameters,
                                                final TransactionPoolConfiguration transactionPoolConfiguration,
                                                final SecurityModule securityModule,
                                                final ObservableMetricsSystem metricsSystem,
                                                final List<NodeMessagePermissioningProvider> messagePermissioningProviders,
                                                final PrivacyParameters privacyParameters,
                                                final @Named("isRevertReasonEnabled") boolean isRevertReasonEnabled,
                                                final StorageProvider storageProvider,
                                                final Map<Long, Hash> requiredBlocks,
                                                final Long reorgLoggingThreshold,
                                                final EvmConfiguration evmConfiguration,
                                                final P2PDiscoveryOptionGroup p2PDiscoveryOptionGroup,
                                                final ChainPruningOptions unstableChainPruningOptions,
                                                final @Named("numberOfBlocksToCache") int numberOfBlocksToCache,
                                                final @Named("genesisStateHashCacheEnabled") boolean genesisStateHashCacheEnabled) {
        BesuController.Builder controllerBuilder = new BesuController.Builder();

        return controllerBuilder
                .fromEthNetworkConfig(ethNetworkConfig, syncMode)
                .synchronizerConfiguration(synchronizerConfiguration)
                .ethProtocolConfiguration(ethProtocolConfiguration)
                .networkConfiguration(networkingConfiguration)
                .dataDirectory(dataDir)
                .dataStorageConfiguration(dataStorageConfiguration)
                .miningParameters(miningParameters)
                .transactionPoolConfiguration(transactionPoolConfiguration)
                .nodeKey(new NodeKey(securityModule))
                .metricsSystem(metricsSystem)
                .messagePermissioningProviders(messagePermissioningProviders)
                .privacyParameters(privacyParameters)
                .clock(Clock.systemUTC())
                .isRevertReasonEnabled(isRevertReasonEnabled)
                .isParallelTxProcessingEnabled(
                        dataStorageConfiguration.getUnstable().isParallelTxProcessingEnabled())
                .storageProvider(storageProvider)
                .gasLimitCalculator(
                        miningParameters.getTargetGasLimit().isPresent()
                                ? new FrontierTargetingGasLimitCalculator()
                                : GasLimitCalculator.constant())
                .requiredBlocks(requiredBlocks)
                .reorgLoggingThreshold(reorgLoggingThreshold)
                .evmConfiguration(evmConfiguration)
                .maxPeers(p2PDiscoveryOptionGroup.maxPeers)
                .maxRemotelyInitiatedPeers(p2PDiscoveryOptionGroup.maxRemoteInitiatedPeers)
                .randomPeerPriority(p2PDiscoveryOptionGroup.randomPeerPriority)
                .chainPruningConfiguration(unstableChainPruningOptions.toDomainObject())
                .cacheLastBlocks(numberOfBlocksToCache)
                .genesisStateHashCacheEnabled(genesisStateHashCacheEnabled)
                .build();
    }


}
