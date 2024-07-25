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
package org.hyperledger.besu.tests.acceptance.bootstrap;

import org.hyperledger.besu.ethereum.core.components.EthereumCoreModule;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCacheModule;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoaderModule;
import org.hyperledger.besu.metrics.MetricsSystemModule;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.ThreadBesuNodeRunner;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;

import java.io.IOException;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterThreadNodeRunnerAcceptanceTest extends AcceptanceTestBase {

  private Node fullNode;
  private Cluster noDiscoveryCluster;

  @BeforeEach
  public void setUp() throws Exception {
    // final ClusterConfiguration clusterConfiguration =
    //  new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    // final BesuNodeRunner besuNodeRunner =
    // DaggerThreadBesuNodeRunner_AcceptanceTestBesuComponent.create().getThreadBesuNodeRunner();
    ClusterThreadNodeRunnerComponent component =
        DaggerClusterThreadNodeRunnerAcceptanceTest_ClusterThreadNodeRunnerComponent.create();
    this.noDiscoveryCluster = component.getCluster();
    // noDiscoveryCluster = new Cluster(clusterConfiguration, net, besuNodeRunner);
    final BesuNode noDiscoveryNode = component.getNoDiscoveryNode();
    this.fullNode = component.getFullNode();
    this.noDiscoveryCluster.start(noDiscoveryNode, this.fullNode);
  }

  @Test
  public void shouldVerifySomething() {
    // we don't care what verifies, just that it gets to the point something can verify
    // DaggerClusterThreadNodeRunnerComponent.create().getCluster().verify();
    fullNode.verify(net.awaitPeerCount(0));
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    noDiscoveryCluster.stop();
    super.tearDownAcceptanceTestBase();
  }

  @Module
  public static class ClusterThreadNodeRunnerModule {

    @Provides
    @Singleton
    BesuNodeFactory provideNodeFactory() {
      return new BesuNodeFactory();
    }

    @Provides
    @Named("noDiscovery")
    BesuNode provideNonDiscoveryBesuNode(final BesuNodeFactory nodeFactory) {
      try {
        return nodeFactory.createNodeWithNoDiscovery("noDiscovery");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Provides
    @Named("fullNode")
    BesuNode provideFullNode(final BesuNodeFactory nodeFactory) {
      try {
        return nodeFactory.createArchiveNode("archive");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Provides
    @Singleton
    ClusterConfiguration provideClusterConfiguration() {
      return new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    }

    @Provides
    @Singleton
    Cluster provideCluster(
        final ClusterConfiguration clusterConfiguration,
        final NetConditions net,
        final ThreadBesuNodeRunner threadBesuNodeRunner) {
      return new Cluster(clusterConfiguration, net, threadBesuNodeRunner);
    }

    @Provides
    @Singleton
    NetConditions provideNet() {
      return new NetConditions(new NetTransactions());
    }
  }

  @Singleton
  @Component(
      modules = {
        ClusterThreadNodeRunnerModule.class,
        ThreadBesuNodeRunner.BesuControllerModule.class,
        ThreadBesuNodeRunner.MockBesuCommandModule.class,
        ThreadBesuNodeRunner.ThreadBesuNodeRunnerModule.class,
        BonsaiCachedMerkleTrieLoaderModule.class,
        MetricsSystemModule.class,
        EthereumCoreModule.class,
        BlobCacheModule.class
      })
  interface ClusterThreadNodeRunnerComponent
      extends ThreadBesuNodeRunner.AcceptanceTestBesuComponent {

    Cluster getCluster();

    @Named("noDiscovery")
    BesuNode getNoDiscoveryNode();

    @Named("fullNode")
    BesuNode getFullNode();
  }
}
