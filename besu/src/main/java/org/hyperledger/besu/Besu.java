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
package org.hyperledger.besu;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.NetworkDeprecationMessage;
import org.hyperledger.besu.cli.logging.BesuLoggingConfigurationFactory;
import org.hyperledger.besu.components.DaggerBesuComponent;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.hyperledger.besu.ethereum.core.VersionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.RunLast;

/** Besu bootstrap class. */
public final class Besu {
  /** Default constructor. */
  public Besu() {}

  /**
   * The main entrypoint to Besu application
   *
   * @param args command line arguments.
   */
  public static void main(final String... args) {
    setupLogging();
    final BesuCommand besuCommand = DaggerBesuComponent.create().getBesuCommand();
    int exitCode =
        besuCommand.parse(
            new RunLast(),
            besuCommand.parameterExceptionHandler(),
            besuCommand.executionExceptionHandler(),
            System.in,
            args);

    System.exit(exitCode);
  }

  public void runBesuCommand() {
    if (network != null && network.isDeprecated()) {
      logger.warn(NetworkDeprecationMessage.generate(network));
    }
    try {
      configureLogging(true);

      // set merge config on the basis of genesis config
      setMergeConfigOptions();

      setIgnorableStorageSegments();

      instantiateSignatureAlgorithmFactory();

      logger.info("Starting Besu");

      // Need to create vertx after cmdline has been parsed, such that metricsSystem is configurable
      vertx = createVertx(createVertxOptions(metricsSystem.get()));

      validateOptions();

      configure();

      // If we're not running against a named network, or if version compat protection has been
      // explicitly enabled, perform compatibility check
      VersionMetadata.versionCompatibilityChecks(versionCompatibilityProtection, dataDir());

      configureNativeLibs();

      besuPluginContext.beforeExternalServices();

      final var runner = buildRunner();
      runner.startExternalServices();

      startPlugins(runner);
      validatePrivacyPluginOptions();
      setReleaseMetrics();
      preSynchronization();

      runner.startEthereumMainLoop();

      besuPluginContext.afterExternalServicesMainLoop();

      runner.awaitStop();

    } catch (final Exception e) {
      logger.error("Failed to start Besu", e);
      throw new CommandLine.ParameterException(this.commandLine, e.getMessage(), e);
    }
  }

  /**
   * a Logger setup for handling any exceptions during the bootstrap process, to indicate to users
   * their CLI configuration had problems.
   */
  public static void setupLogging() {
    try {
      InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    } catch (Throwable t) {
      System.out.printf(
          "Could not set netty log4j logger factory: %s - %s%n",
          t.getClass().getSimpleName(), t.getMessage());
    }
    try {
      System.setProperty(
          "vertx.logger-delegate-factory-class-name",
          "io.vertx.core.logging.Log4j2LogDelegateFactory");
      System.setProperty(
          "log4j.configurationFactory", BesuLoggingConfigurationFactory.class.getName());
      System.setProperty("log4j.skipJansi", String.valueOf(false));
    } catch (Throwable t) {
      System.out.printf(
          "Could not set logging system property: %s - %s%n",
          t.getClass().getSimpleName(), t.getMessage());
    }
  }

  /**
   * Returns the first logger to be created. This is used to set the default uncaught exception
   *
   * @return Logger
   */
  public static Logger getFirstLogger() {
    final Logger logger = LoggerFactory.getLogger(Besu.class);
    Thread.setDefaultUncaughtExceptionHandler(slf4jExceptionHandler(logger));
    Thread.currentThread().setUncaughtExceptionHandler(slf4jExceptionHandler(logger));

    return logger;
  }

  private static Thread.UncaughtExceptionHandler slf4jExceptionHandler(final Logger logger) {
    return (thread, error) -> {
      if (logger.isErrorEnabled()) {
        logger.error(String.format("Uncaught exception in thread \"%s\"", thread.getName()), error);
      }
    };
  }
}
