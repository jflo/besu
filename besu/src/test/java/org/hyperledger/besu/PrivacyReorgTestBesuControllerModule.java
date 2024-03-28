/*
 * Copyright Hyperledger Besu contributors.
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

package org.hyperledger.besu;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import javax.inject.Named;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.nio.file.Path;

@Module
public class PrivacyReorgTestBesuControllerModule {


    @Provides
    @Singleton
    @SuppressWarnings("CloseableProvides")
    BesuController provideBesuController(final PrivacyParameters privacyParameters,
                                     final GenesisConfigFile genesisConfigFile,
                                     final @Named("dataDir") Path dataDir) {


        //dataStorageConfiguration default
        //named privacyReorgParams
        BesuController retval = new BesuController.Builder()
                .fromGenesisConfig(genesisConfigFile,
                        SyncMode.FULL)
                .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
                .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
                .storageProvider(new InMemoryKeyValueStorageProvider())
                .networkId(BigInteger.ONE)
                .miningParameters(MiningParameters.newDefault())
                .nodeKey(NodeKeyUtils.generate())
                .metricsSystem(new NoOpMetricsSystem())
                .dataDirectory(dataDir)
                .clock(TestClock.fixed())
                .privacyParameters(privacyParameters)
                .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
                .gasLimitCalculator(GasLimitCalculator.constant())
                .evmConfiguration(EvmConfiguration.DEFAULT)
                .networkConfiguration(NetworkingConfiguration.create())
                .build();
        return retval;
    }
}
