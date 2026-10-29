/*
 * Copyright contributors to Besu.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.ethereum.core.plugins.ImmutablePluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.ParameterException;

public class BesuPluginContextImplTest {

  interface TestServiceA extends BesuService {}

  interface TestServiceB extends BesuService {}

  interface TestServiceC extends BesuService {}

  /** Implemented by the test plugins compiled by {@link #writePluginJar}, to record their calls. */
  public interface LifecycleRecorder {
    List<String> events();
  }

  @TempDir Path tempDir;

  @Test
  void serviceRegistrySupportsBasicAddAndGet() {
    final BesuPluginContextImpl context = new BesuPluginContextImpl();
    final TestServiceA serviceA = new TestServiceA() {};

    context.addService(TestServiceA.class, serviceA);

    final Optional<TestServiceA> retrieved = context.getService(TestServiceA.class);
    assertThat(retrieved).isPresent().contains(serviceA);
  }

  @Test
  void getServiceReturnsEmptyForUnregisteredService() {
    final BesuPluginContextImpl context = new BesuPluginContextImpl();

    final Optional<TestServiceA> retrieved = context.getService(TestServiceA.class);
    assertThat(retrieved).isEmpty();
  }

  @Test
  void serviceRegistryHandlesConcurrentReadsAndWrites() throws Exception {
    final BesuPluginContextImpl context = new BesuPluginContextImpl();
    final int threadCount = 10;
    final int operationsPerThread = 100;
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final AtomicBoolean failed = new AtomicBoolean(false);
    final List<Future<?>> futures = new ArrayList<>();

    // Pre-register one service so readers have something to find
    final TestServiceA serviceA = new TestServiceA() {};
    context.addService(TestServiceA.class, serviceA);

    // Half the threads write services, half read services concurrently
    for (int i = 0; i < threadCount; i++) {
      final int threadIndex = i;
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  for (int op = 0; op < operationsPerThread; op++) {
                    if (threadIndex % 2 == 0) {
                      // Writer thread: repeatedly overwrite services
                      context.addService(TestServiceB.class, new TestServiceB() {});
                    } else {
                      // Reader thread: concurrently read services
                      context.getService(TestServiceA.class);
                      context.getService(TestServiceB.class);
                    }
                  }
                } catch (final Exception e) {
                  failed.set(true);
                }
              }));
    }

    // Start all threads simultaneously
    startLatch.countDown();

    for (final Future<?> future : futures) {
      future.get(10, TimeUnit.SECONDS);
    }

    executor.shutdown();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(failed.get()).isFalse();

    // Verify services are still accessible after concurrent operations
    assertThat(context.getService(TestServiceA.class)).isPresent().contains(serviceA);
    assertThat(context.getService(TestServiceB.class)).isPresent();
  }

  @Test
  void multipleServicesCanBeRegisteredAndRetrieved() {
    final BesuPluginContextImpl context = new BesuPluginContextImpl();
    final TestServiceA serviceA = new TestServiceA() {};
    final TestServiceB serviceB = new TestServiceB() {};
    final TestServiceC serviceC = new TestServiceC() {};

    context.addService(TestServiceA.class, serviceA);
    context.addService(TestServiceB.class, serviceB);
    context.addService(TestServiceC.class, serviceC);

    assertThat(context.getService(TestServiceA.class)).isPresent().contains(serviceA);
    assertThat(context.getService(TestServiceB.class)).isPresent().contains(serviceB);
    assertThat(context.getService(TestServiceC.class)).isPresent().contains(serviceC);
  }

  @Test
  void bundledPluginsAreRegisteredEvenWhenExternalPluginsAreDisabled() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "BundledTestPlugin");

    final BesuPluginContextImpl context =
        registeredContext(configuration(bundledDir).externalPluginsEnabled(false).build());

    assertThat(registeredPluginNames(context)).containsExactly("BundledTestPlugin");
    assertThat(context.getRegisteredBundledPlugins()).containsExactly("BundledTestPlugin");
  }

  @Test
  void bundledPluginsAreRegisteredWhenOtherPluginsAreRequested() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "BundledTestPlugin");
    final Path pluginsDir = writePluginJar("plugins", "ExternalTestPlugin");
    writePluginJar("plugins", "UnrequestedTestPlugin");

    final BesuPluginContextImpl context =
        registeredContext(
            configuration(bundledDir)
                .pluginsDir(pluginsDir)
                .requestedPluginsInfo(List.of(new PluginInfo("ExternalTestPlugin")))
                .build());

    assertThat(registeredPluginNames(context))
        .containsExactlyInAnyOrder("ExternalTestPlugin", "BundledTestPlugin");
    assertThat(context.getRegisteredBundledPlugins()).containsExactly("BundledTestPlugin");
  }

  @Test
  void pluginInstalledInThePluginsDirectoryReplacesTheBundledPluginOfTheSameClass()
      throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "GraphQLTestPlugin");
    final Path pluginsDir = writePluginJar("plugins", "GraphQLTestPlugin");

    final BesuPluginContextImpl context =
        registeredContext(configuration(bundledDir).pluginsDir(pluginsDir).build());

    assertThat(registeredPluginNames(context)).containsExactly("GraphQLTestPlugin");
    assertThat(context.getRegisteredBundledPlugins()).isEmpty();
  }

  @Test
  void stopPluginsForRestartStopsThePluginsAndKeepsThemRegistered() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "BundledTestPlugin");
    final BesuPluginContextImpl context = registeredContext(configuration(bundledDir).build());
    final LifecycleRecorder plugin = (LifecycleRecorder) context.getRegisteredPlugins().get(0);

    context.beforeExternalServices();
    context.startPlugins();
    context.stopPluginsForRestart();
    // the same plugin instance goes through the lifecycle again, without being registered again
    context.beforeExternalServices();
    context.startPlugins();
    context.stopPlugins();

    assertThat(plugin.events()).containsExactly("register", "start", "stop", "start", "stop");
  }

  @Test
  void pluginRejectingItsOptionsFailsTheCommandLineEvenWhenContinuingOnPluginErrors()
      throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "RejectingOptionsTestPlugin");
    final BesuPluginContextImpl context =
        registeredContext(configuration(bundledDir).continueOnPluginError(true).build());

    assertThatExceptionOfType(ParameterException.class)
        .isThrownBy(context::beforeExternalServices)
        .withMessage("--plugin-test-option must be >= 0");
  }

  @Test
  void pluginFailingToStartFailsStartupWithTheCause() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "FailingStartTestPlugin");
    final BesuPluginContextImpl context = registeredContext(configuration(bundledDir).build());
    context.beforeExternalServices();

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(context::startPlugins)
        .withMessage(
            "Error starting plugin of type test.plugins.FailingStartTestPlugin: Address already in use");
  }

  @Test
  void bundledPluginFailingToStartFailsStartupEvenWhenContinuingOnPluginErrors() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "FailingStartTestPlugin");
    final BesuPluginContextImpl context =
        registeredContext(configuration(bundledDir).continueOnPluginError(true).build());
    context.beforeExternalServices();

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(context::startPlugins)
        .withMessage(
            "Error starting plugin of type test.plugins.FailingStartTestPlugin: Address already in use");
  }

  @Test
  void externalPluginFailingToStartIsDroppedWhenContinuingOnPluginErrors() throws Exception {
    final Path pluginsDir = writePluginJar("plugins", "FailingStartTestPlugin");
    final BesuPluginContextImpl context =
        registeredContext(
            configuration(tempDir.resolve("no-bundled-plugins"))
                .pluginsDir(pluginsDir)
                .continueOnPluginError(true)
                .build());
    context.beforeExternalServices();

    context.startPlugins();

    assertThat(context.getRegisteredPlugins()).isEmpty();
  }

  @Test
  void onlyBundledPluginsAreRegisteringAsBundled() throws Exception {
    final Path bundledDir = writePluginJar("bundled-plugins", "ProbingBundledTestPlugin");
    final Path pluginsDir = writePluginJar("plugins", "ProbingExternalTestPlugin");

    final BesuPluginContextImpl context =
        registeredContext(configuration(bundledDir).pluginsDir(pluginsDir).build());

    assertThat(context.getRegisteredPlugins())
        .map(plugin -> ((LifecycleRecorder) plugin).events())
        .containsExactlyInAnyOrder(
            List.of("register", "bundled=false"), List.of("register", "bundled=true"));
    assertThat(context.isRegisteringBundledPlugin()).isFalse();
  }

  private ImmutablePluginConfiguration.Builder configuration(final Path bundledDir) {
    return ImmutablePluginConfiguration.builder()
        .pluginsDir(tempDir.resolve("no-plugins"))
        .bundledPluginsDir(bundledDir);
  }

  private static BesuPluginContextImpl registeredContext(final PluginConfiguration config) {
    final BesuPluginContextImpl context = new BesuPluginContextImpl();
    context.initialize(config);
    context.registerPlugins();
    return context;
  }

  private static List<String> registeredPluginNames(final BesuPluginContextImpl context) {
    return context.getRegisteredPlugins().stream()
        .map(plugin -> plugin.getClass().getSimpleName())
        .toList();
  }

  /**
   * Compiles a plugin class that is not on the test class path and writes it, with its service
   * file, to a jar in the given directory under the temporary directory.
   */
  private Path writePluginJar(final String dirName, final String className) throws IOException {
    final Path dir = Files.createDirectories(tempDir.resolve(dirName));
    final Path srcDir =
        Files.createDirectories(tempDir.resolve("src-" + dirName + "-" + className));
    final Path source = srcDir.resolve(className + ".java");
    Files.writeString(
        source,
        """
        package test.plugins;

        public class %s implements org.hyperledger.besu.plugin.BesuPlugin,
            org.hyperledger.besu.services.BesuPluginContextImplTest.LifecycleRecorder {
          private final java.util.List<String> events = new java.util.ArrayList<>();

          @Override
          public java.util.List<String> events() {
            return events;
          }

          @Override
          public void register(final org.hyperledger.besu.plugin.ServiceManager context) {
            events.add("register");
            if (getClass().getSimpleName().startsWith("Probing")) {
              try {
                events.add(
                    "bundled="
                        + context.getClass().getMethod("isRegisteringBundledPlugin").invoke(context));
              } catch (final ReflectiveOperationException e) {
                throw new IllegalStateException(e);
              }
            }
          }

          @Override
          public void beforeExternalServices() {
            if (getClass().getSimpleName().startsWith("Rejecting")) {
              throw new picocli.CommandLine.ParameterException(
                  new picocli.CommandLine(picocli.CommandLine.Model.CommandSpec.create()),
                  "--plugin-test-option must be >= 0");
            }
          }

          @Override
          public void start() {
            if (getClass().getSimpleName().startsWith("FailingStart")) {
              throw new IllegalStateException("Address already in use");
            }
            events.add("start");
          }

          @Override
          public void stop() {
            events.add("stop");
          }
        }
        """
            .formatted(className),
        UTF_8);
    final String classPath =
        String.join(
            java.io.File.pathSeparator,
            codeSource(BesuPlugin.class),
            codeSource(BesuService.class),
            codeSource(ParameterException.class),
            codeSource(BesuPluginContextImplTest.class));
    final int result =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-proc:none",
                "-cp",
                classPath,
                "-d",
                srcDir.toString(),
                source.toString());
    assertThat(result).isZero();

    try (final JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(dir.resolve(className + ".jar")))) {
      jar.putNextEntry(new JarEntry("test/plugins/" + className + ".class"));
      Files.copy(srcDir.resolve("test/plugins/" + className + ".class"), (OutputStream) jar);
      jar.putNextEntry(new JarEntry("META-INF/services/" + BesuPlugin.class.getName()));
      jar.write(("test.plugins." + className + "\n").getBytes(UTF_8));
    }
    return dir;
  }

  private static String codeSource(final Class<?> clazz) {
    try {
      return Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    } catch (final java.net.URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
