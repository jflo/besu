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
import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.util.LogConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

@Module
public class RunnerModule {

    private final Logger logger = LoggerFactory.getLogger(RunnerModule.class);

    @Provides
    @Singleton
    Runner provideRunner(final Optional<TLSConfiguration> tlsConfiguration,
                         final ObservableMetricsSystem metricsSystem) {
        RunnerBuilder runnerBuilder = new RunnerBuilder();
        tlsConfiguration.ifPresent(runnerBuilder::p2pTLSConfiguration);

        final Runner runner =
                runnerBuilder
                        .vertx(vertx)
                        .besuController(besuController)
                        .p2pEnabled(p2PDiscoveryOptionGroup.p2pEnabled)
                        .natMethod(natMethod)
                        .natManagerServiceName(unstableNatOptions.getNatManagerServiceName())
                        .natMethodFallbackEnabled(unstableNatOptions.getNatMethodFallbackEnabled())
                        .discovery(p2PDiscoveryOptionGroup.peerDiscoveryEnabled)
                        .ethNetworkConfig(ethNetworkConfig)
                        .permissioningConfiguration(permissioningConfiguration)
                        .p2pAdvertisedHost(p2PDiscoveryOptionGroup.p2pHost)
                        .p2pListenInterface(p2PDiscoveryOptionGroup.p2pInterface)
                        .p2pListenPort(p2PDiscoveryOptionGroup.p2pPort)
                        .networkingConfiguration(unstableNetworkingOptions.toDomainObject())
                        .legacyForkId(unstableEthProtocolOptions.toDomainObject().isLegacyEth64ForkIdEnabled())
                        .graphQLConfiguration(graphQLConfiguration)
                        .jsonRpcConfiguration(jsonRpcConfiguration)
                        .engineJsonRpcConfiguration(engineJsonRpcConfiguration)
                        .webSocketConfiguration(webSocketConfiguration)
                        .jsonRpcIpcConfiguration(jsonRpcIpcConfiguration)
                        .inProcessRpcConfiguration(inProcessRpcConfiguration)
                        .apiConfiguration(apiConfiguration)
                        .pidPath(pidPath)
                        .dataDir(dataDir())
                        .bannedNodeIds(p2PDiscoveryOptionGroup.bannedNodeIds)
                        .metricsSystem(metricsSystem)
                        .permissioningService(permissioningService)
                        .metricsConfiguration(metricsConfiguration)
                        .staticNodes(staticNodes)
                        .identityString(identityString)
                        .besuPluginContext(besuPluginContext)
                        .autoLogBloomCaching(autoLogBloomCachingEnabled)
                        .ethstatsOptions(ethstatsOptions)
                        .storageProvider(keyValueStorageProvider())
                        .rpcEndpointService(rpcEndpointServiceImpl)
                        .enodeDnsConfiguration(getEnodeDnsConfiguration())
                        .allowedSubnets(p2PDiscoveryOptionGroup.allowedSubnets)
                        .poaDiscoveryRetryBootnodes(p2PDiscoveryOptionGroup.poaDiscoveryRetryBootnodes)
                        .build();

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        besuPluginContext.stopPlugins();
                                        runner.close();
                                        LogConfigurator.shutdown();
                                    } catch (final Exception e) {
                                        logger.error("Failed to stop Besu");
                                    }
                                },
                                "BesuCommand-Shutdown-Hook"));

        return runnerBuilder.build();
    }

}
