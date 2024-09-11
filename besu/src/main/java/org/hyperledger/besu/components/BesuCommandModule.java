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

import org.hyperledger.besu.Besu;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.options.stable.JsonRpcHttpOptions;
import org.hyperledger.besu.cli.options.unstable.ChainPruningOptions;
import org.hyperledger.besu.cli.util.BesuCommandCustomFactory;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.services.BesuPluginContextImpl;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.slf4j.Logger;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A dagger module that know how to create the BesuCommand, which collects all configuration
 * settings.
 */
@Module
public class BesuCommandModule {
  /** Default constructor. */
  public BesuCommandModule() {}

  @Provides
  @Singleton
  BesuCommand provideBesuCommand(
      final @Named("besuCommandLogger") Logger commandLogger) {
    final BesuCommand besuCommand =
        new BesuCommand(
            RlpBlockImporter::new,
            JsonBlockImporter::new,
            RlpBlockExporter::new,
            System.getenv(),
            commandLogger);

    return besuCommand;
  }

  @Provides
  @Named("dataDir")
  @Singleton
  Path provideDataDir(final BesuCommand provideFrom) {
    return provideFrom.dataDir().toAbsolutePath();
  }

  @Provides
  @Singleton
  DataStorageConfiguration provideDataStorageConfiguration(final BesuCommand provideFrom) {
    return provideFrom.getDataStorageConfiguration();
  }

  @Provides
  @Singleton
  MiningParameters provideMiningParameters(final BesuCommand provideFrom) {
    return provideFrom.getMiningParameters();
  }

  @Provides
  @Singleton
  MetricsConfiguration provideMetricsConfiguration(final BesuCommand provideFrom) {
    return provideFrom.metricsConfiguration();
  }

  @Provides
  @Singleton
  JsonRpcHttpOptions provideJsonRpcHttpOptions(final BesuCommand provideFrom) {
    return provideFrom.getJsonRpcHttpOptions();
  }

  @Provides
  @Named("besuCommandLogger")
  @Singleton
  Logger provideBesuCommandLogger() {
    return Besu.getFirstLogger();
  }

  @Provides
  @Singleton
  EthNetworkConfig provideEthNetworkConfig(final BesuCommand provideFrom) {
    return provideFrom.getEthNetworkConfig();
  }

  @Provides
  @Singleton
  SyncMode provideSyncMode(final BesuCommand provideFrom) {
    return provideFrom.getDefaultSyncModeIfNotSet();
  }

  @Provides
  @Singleton
  SynchronizerConfiguration provideSynchronizerConfiguration(final BesuCommand provideFrom) {
    return provideFrom.buildSyncConfig();
  }

  @Provides
  @Singleton
  EthProtocolConfiguration provideEthProtocolConfiguration(final BesuCommand provideFrom) {
    return provideFrom.getUnstableEthProtocolOptions().toDomainObject();
  }

  @Provides
  @Singleton
  NetworkingConfiguration provideNetworkingConfiguration(final BesuCommand provideFrom) {
    return provideFrom.getUnstableNetworkingOptions().toDomainObject();
  }

  @Provides
  @Singleton
  TransactionPoolConfiguration provideTransactionPoolConfiguration(final BesuCommand provideFrom) {
    return provideFrom.buildTransactionPoolConfiguration();
  }

  @Provides
  @Singleton
  SecurityModule provideSecurityModule(final BesuCommand provideFrom) {
    return provideFrom.securityModule();
  }

  @Provides
  @Singleton
  List<NodeMessagePermissioningProvider> provideMessagePermissioningProviders(final BesuCommand provideFrom) {
    return provideFrom.getPermissioningService().getMessagePermissioningProviders();
  }

  @Provides
  @Singleton
  PrivacyParameters providePrivacyParameters(final BesuCommand provideFrom) {
    return provideFrom.privacyParameters();
  }

  @Provides
  @Singleton
  @Named("isRevertReasonEnabled")
    boolean provideIsRevertReasonEnabled(final BesuCommand provideFrom) {
        return provideFrom.isRevertReasonEnabled;
    }

  @Provides
  @Singleton
  CommandLine toCommandLine(final BesuCommand besuCommand, final BesuPluginContextImpl besuPluginContext) {
    CommandLine retval = new CommandLine(besuCommand, new BesuCommandCustomFactory(besuPluginContext))
                    .setCaseInsensitiveEnumValuesAllowed(true);
    retval.getCommandSpec().usageMessage().autoWidth(true);
    return retval;
  }

  @Provides
  @Singleton
  StorageProvider provideStorageProvider(final BesuCommand provideFrom) {
    return provideFrom.getStorageProvider();
  }

  @Provides
  @Singleton
  Map<Long, Hash> provideRequiredBlocks(final BesuCommand provideFrom) {
    return provideFrom.getRequiredBlocks();
  }

  @Provides
  @Singleton
  @Named("reorgLoggingThreshold")
  Long provideReorgLoggingThreshold(final BesuCommand provideFrom) {
    return provideFrom.getReorgLoggingThreshold();
  }

  @Provides
  @Singleton
  ChainPruningOptions provideChainPruningOptions(final BesuCommand provideFrom) {
    return provideFrom.getUnstableChainPruningOptions();
  }

  @Provides
  @Singleton
  @Named("numberOfBlocksToCache")
  int provideNumberOfBlocksToCache(final BesuCommand provideFrom) {
      return provideFrom.numberOfblocksToCache;
  }

  @Provides
  @Singleton
  @Named("genesisStateHashCacheEnabled")
  boolean provideGenesisStateHashCacheEnabled(final BesuCommand provideFrom) {
      return provideFrom.genesisStateHashCacheEnabled;
  }

  @Provides
  @Singleton
  Optional<TLSConfiguration> provideP2pTLSConfiguration(final BesuCommand provideFrom) {
    return provideFrom.getP2pTLSConfiguration();
  }
}
