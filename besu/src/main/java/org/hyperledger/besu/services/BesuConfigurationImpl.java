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
package org.hyperledger.besu.services;

import org.hyperledger.besu.cli.options.stable.JsonRpcHttpOptions;
import org.hyperledger.besu.Besu;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;

/** A concrete implementation of BesuConfiguration which is used in Besu plugin framework. */
public class BesuConfigurationImpl implements BesuConfiguration {
  private Path storagePath;
  private Path dataPath;
  private DataStorageConfiguration dataStorageConfiguration;

  // defaults
  private MiningParameters miningParameters = MiningParameters.newDefault();
  private Optional<String> rpcHttpHost = Optional.of("http://localhost");
  private Optional<Integer> rpcHttpPort = Optional.of(8545);

  /** Default Constructor. */
  @Inject
  public BesuConfigurationImpl() {}

  /**
   * Post creation initialization
   *
   * @param dataPath The Path representing data folder
   * @param storagePath The path representing storage folder
   * @param dataStorageConfiguration The data storage configuration
   * @return BesuConfigurationImpl instance
   */
  public BesuConfigurationImpl init(
      final Path dataPath,
      final Path storagePath,
      final DataStorageConfiguration dataStorageConfiguration) {
    this.dataPath = dataPath;
    this.storagePath = storagePath;
    this.dataStorageConfiguration = dataStorageConfiguration;
    return this;
  }

  /**
   * Set the mining parameters
   *
   * @param miningParameters configured mining parameters
   * @return BesuConfigurationImpl instance
   */
  public BesuConfigurationImpl withMiningParameters(final MiningParameters miningParameters) {
    this.miningParameters = miningParameters;
    return this;
  }

  /**
   * Set the RPC http options
   *
   * @param rpcHttpOptions configured rpc http options
   * @return BesuConfigurationImpl instance
   */
  public BesuConfigurationImpl withJsonRpcHttpOptions(final JsonRpcHttpOptions rpcHttpOptions) {
    this.rpcHttpHost = Optional.ofNullable(rpcHttpOptions.getRpcHttpHost());
    this.rpcHttpPort = Optional.ofNullable(rpcHttpOptions.getRpcHttpPort());
    return this;
  }

  @Override
  public Optional<String> getRpcHttpHost() {
    return rpcHttpHost;
  }

  @Override
  public Optional<Integer> getRpcHttpPort() {
    return rpcHttpPort;
  }

  @Override
  public Path getStoragePath() {
    return storagePath;
  }

  @Override
  public Path getDataPath() {
    return dataPath;
  }

  @Override
  public DataStorageFormat getDatabaseFormat() {
    return dataStorageConfiguration.getDataStorageFormat();
  }

  @Override
  public Wei getMinGasPrice() {
    return miningParameters.getMinTransactionGasPrice();
  }

  @Override
  public org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration
      getDataStorageConfiguration() {
    return new DataStoreConfigurationImpl(dataStorageConfiguration);
  }

  /**
   * A concrete implementation of DataStorageConfiguration which is used in Besu plugin framework.
   */
  public static class DataStoreConfigurationImpl
      implements org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration {

    private final DataStorageConfiguration dataStorageConfiguration;

    /**
     * Instantiate the concrete implementation of the plugin DataStorageConfiguration.
     *
     * @param dataStorageConfiguration The Ethereum core module data storage configuration
     */
    public DataStoreConfigurationImpl(final DataStorageConfiguration dataStorageConfiguration) {
      this.dataStorageConfiguration = dataStorageConfiguration;
    }

    @Override
    public DataStorageFormat getDatabaseFormat() {
      return dataStorageConfiguration.getDataStorageFormat();
    }

    @Override
    public boolean getReceiptCompactionEnabled() {
      return dataStorageConfiguration.getReceiptCompactionEnabled();
    }
  }
}
