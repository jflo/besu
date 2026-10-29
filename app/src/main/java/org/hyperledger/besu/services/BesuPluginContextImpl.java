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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toSet;

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ParameterException;

/** The Besu plugin context implementation. */
// PluginVersionsProvider is deprecated for removal from the plugin API
@SuppressWarnings("removal")
public class BesuPluginContextImpl implements ServiceManager, PluginVersionsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BesuPluginContextImpl.class);

  private enum Lifecycle {
    /** Uninitialized lifecycle. */
    UNINITIALIZED,
    /** Initialized lifecycle. */
    INITIALIZED,
    /** Registering lifecycle. */
    REGISTERING,
    /** Registered lifecycle. */
    REGISTERED,
    /** Before external services started lifecycle. */
    BEFORE_EXTERNAL_SERVICES_STARTED,
    /** Before external services finished lifecycle. */
    BEFORE_EXTERNAL_SERVICES_FINISHED,
    /** Before main loop started lifecycle. */
    BEFORE_MAIN_LOOP_STARTED,
    /** Before main loop finished lifecycle. */
    BEFORE_MAIN_LOOP_FINISHED,
    /** Stopping lifecycle. */
    STOPPING,
    /** Stopped lifecycle. */
    STOPPED
  }

  private Lifecycle state = Lifecycle.UNINITIALIZED;
  private final Map<Class<?>, ? super BesuService> serviceRegistry = new ConcurrentHashMap<>();

  private List<BesuPlugin> detectedPlugins = new ArrayList<>();
  private List<String> requestedPlugins = new ArrayList<>();

  private final List<BesuPlugin> registeredPlugins = new ArrayList<>();
  private final List<BesuPlugin> bundledPlugins = new ArrayList<>();
  private BesuPlugin registeringPlugin;

  private final Map<String, String> pluginVersions = new LinkedHashMap<>();
  private PluginConfiguration config;
  private final List<URLClassLoader> pluginClassLoaders = new ArrayList<>();

  /** Instantiates a new Besu plugin context. */
  public BesuPluginContextImpl() {}

  /**
   * Add service.
   *
   * @param <T> the type parameter
   * @param serviceType the service type
   * @param service the service
   */
  @Override
  public <T extends BesuService> void addService(final Class<T> serviceType, final T service) {
    checkArgument(serviceType.isInterface(), "Services must be Java interfaces.");
    checkArgument(
        serviceType.isInstance(service),
        "The service registered with a type must implement that type");
    serviceRegistry.put(serviceType, service);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BesuService> Optional<T> getService(final Class<T> serviceType) {
    return Optional.ofNullable((T) serviceRegistry.get(serviceType));
  }

  /**
   * Initializes the plugin context with the provided {@link PluginConfiguration}.
   *
   * @param config the plugin configuration
   * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
   */
  public void initialize(final PluginConfiguration config) {
    checkState(
        state == Lifecycle.UNINITIALIZED,
        "Besu plugins have already been initialized. Cannot register additional plugins.");
    this.config = config;
    state = Lifecycle.INITIALIZED;
  }

  /**
   * Registers plugins based on the provided {@link PluginConfiguration}. This method finds plugins
   * according to the configuration settings, filters them if necessary and then registers the
   * filtered or found plugins.
   *
   * <p>Plugins bundled with the Besu distribution (in {@link
   * PluginConfiguration#getBundledPluginsDir()}) are part of Besu: they are always registered,
   * whatever {@code --plugins} and {@code --Xplugins-external-enabled} say, unless a plugin of the
   * same class is installed in the plugins directory, which then replaces the bundled one. Their
   * errors are fatal, even with {@code --plugin-continue-on-error}.
   *
   * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
   */
  public void registerPlugins() {
    checkState(
        state == Lifecycle.INITIALIZED,
        "Besu plugins have already been registered. Cannot register additional plugins.");
    state = Lifecycle.REGISTERING;

    final List<BesuPlugin> externalPlugins =
        config.isExternalPluginsEnabled() ? detectPlugins(config) : List.of();
    final Set<String> externalClassNames =
        externalPlugins.stream().map(plugin -> plugin.getClass().getName()).collect(toSet());
    detectBundledPlugins(config).stream()
        .filter(plugin -> !externalClassNames.contains(plugin.getClass().getName()))
        .forEach(bundledPlugins::add);
    detectedPlugins = Stream.concat(externalPlugins.stream(), bundledPlugins.stream()).toList();

    final List<BesuPlugin> registeringPlugins = new ArrayList<>();
    if (!config.isExternalPluginsEnabled()) {
      LOG.debug("External plugins are disabled. Only bundled plugins are registered.");
    } else if (config.getRequestedPlugins().isEmpty()) {
      // If no plugins were specified, register all detected plugins
      registeringPlugins.addAll(externalPlugins);
    } else {
      // Register only the plugins that were explicitly requested and validated
      requestedPlugins = config.getRequestedPlugins();
      // Match and validate the requested plugins against the detected plugins
      registeringPlugins.addAll(
          matchAndValidateRequestedPlugins(requestedPlugins, detectedPlugins));
    }
    bundledPlugins.stream()
        .filter(plugin -> !registeringPlugins.contains(plugin))
        .forEach(registeringPlugins::add);
    registerPlugins(registeringPlugins);
    state = Lifecycle.REGISTERED;
  }

  private List<BesuPlugin> matchAndValidateRequestedPlugins(
      final List<String> requestedPluginNames, final List<BesuPlugin> detectedPlugins)
      throws NoSuchElementException {

    // Filter detected plugins to include only those that match the requested names
    List<BesuPlugin> matchingPlugins =
        detectedPlugins.stream()
            .filter(plugin -> requestedPluginNames.contains(plugin.getClass().getSimpleName()))
            .toList();

    // Check if all requested plugins were found among the detected plugins
    if (matchingPlugins.size() != requestedPluginNames.size()) {
      // Find which requested plugins were not matched to throw a detailed exception
      Set<String> matchedPluginNames =
          matchingPlugins.stream()
              .map(plugin -> plugin.getClass().getSimpleName())
              .collect(Collectors.toSet());
      String missingPlugins =
          requestedPluginNames.stream()
              .filter(name -> !matchedPluginNames.contains(name))
              .collect(Collectors.joining(", "));
      throw new NoSuchElementException(
          "The following requested plugins were not found: " + missingPlugins);
    }
    return matchingPlugins;
  }

  private void registerPlugins(final List<BesuPlugin> pluginsToRegister) {

    for (final BesuPlugin plugin : pluginsToRegister) {
      if (registerPlugin(plugin)) {
        registeredPlugins.add(plugin);
      }
    }
  }

  private boolean registerPlugin(final BesuPlugin plugin) {
    registeringPlugin = plugin;
    try {
      plugin.register(this);
      pluginVersions.put(plugin.getName(), plugin.getVersion());
      LOG.info("Registered plugin of type {}.", plugin.getClass().getName());
    } catch (final Exception e) {
      if (continuesOnError(plugin)) {
        LOG.error(
            "Error registering plugin of type {}, start and stop will not be called.",
            plugin.getClass().getName(),
            e);
      } else {
        throw new RuntimeException(
            "Error registering plugin of type " + plugin.getClass().getName(), e);
      }
      return false;
    } finally {
      registeringPlugin = null;
    }
    return true;
  }

  /**
   * Whether the plugin being registered, if any, is bundled with Besu. Only bundled plugins may
   * register options under the legacy names of the features they took over from Besu (see {@link
   * PicoCLIOptionsImpl}).
   *
   * @return true while a plugin bundled with Besu is registering
   */
  public boolean isRegisteringBundledPlugin() {
    final BesuPlugin plugin = registeringPlugin;
    return plugin != null && bundledPlugins.contains(plugin);
  }

  // Bundled plugins are part of Besu: their errors are fatal, as Besu's own would be, whatever
  // --plugin-continue-on-error says.
  private boolean continuesOnError(final BesuPlugin plugin) {
    return config.isContinueOnPluginError() && !bundledPlugins.contains(plugin);
  }

  /** Before external services. */
  public void beforeExternalServices() {
    checkState(
        state == Lifecycle.REGISTERED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.REGISTERED,
        state);
    state = Lifecycle.BEFORE_EXTERNAL_SERVICES_STARTED;
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();

      try {
        plugin.beforeExternalServices();
        if (LOG.isDebugEnabled()) {
          LOG.debug(
              "beforeExternalServices called on plugin of type {}.", plugin.getClass().getName());
        }
      } catch (final ParameterException e) {
        // a plugin that rejects the values of its options fails the command line, as Besu's own
        // option checks do
        throw e;
      } catch (final Exception e) {
        if (continuesOnError(plugin)) {
          LOG.error(
              "Error calling `beforeExternalServices` on plugin of type {}, start will not be called.",
              plugin.getClass().getName(),
              e);
          pluginsIterator.remove();
        } else {
          throw new RuntimeException(
              "Error calling `beforeExternalServices` on plugin of type "
                  + plugin.getClass().getName(),
              e);
        }
      }
    }
    LOG.debug("Plugin startup complete.");
    state = Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED;
  }

  /** Start plugins. */
  public void startPlugins() {
    checkState(
        state == Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
        state);
    state = Lifecycle.BEFORE_MAIN_LOOP_STARTED;
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();

      try {
        plugin.start();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Started plugin of type {}.", plugin.getClass().getName());
        }
      } catch (final Exception e) {
        if (continuesOnError(plugin)) {
          LOG.error(
              "Error starting plugin of type {}, stop will not be called.",
              plugin.getClass().getName(),
              e);
          pluginsIterator.remove();
        } else {
          // with the cause, so that the reason, such as a port in use, is in Besu's startup error
          throw new RuntimeException(
              "Error starting plugin of type "
                  + plugin.getClass().getName()
                  + ": "
                  + e.getMessage(),
              e);
        }
      }
    }

    LOG.debug("Plugin startup complete.");
    state = Lifecycle.BEFORE_MAIN_LOOP_FINISHED;
  }

  /** Execute all plugin setup code after external services. */
  public void afterExternalServicesMainLoop() {
    checkState(
        state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        state);
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();
      try {
        plugin.afterExternalServicePostMainLoop();
      } catch (final Exception e) {
        if (continuesOnError(plugin)) {
          LOG.error(
              "Error calling `afterExternalServicePostMainLoop` on plugin of type "
                  + plugin.getClass().getName()
                  + ", stop will not be called.",
              e);
          pluginsIterator.remove();
        } else {
          throw new RuntimeException(
              "Error calling `afterExternalServicePostMainLoop` on plugin of type "
                  + plugin.getClass().getName(),
              e);
        }
      }
    }
  }

  /** Stop plugins. */
  public void stopPlugins() {
    checkState(
        state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        state);
    state = Lifecycle.STOPPING;

    stopRegisteredPlugins();

    // Close the plugin classloaders to release file handles
    for (final URLClassLoader classLoader : pluginClassLoaders) {
      try {
        classLoader.close();
        LOG.debug("Closed plugin classloader");
      } catch (final IOException e) {
        LOG.debug("Error closing plugin classloader", e);
      }
    }

    LOG.debug("Plugin shutdown complete.");
    state = Lifecycle.STOPPED;
  }

  /**
   * Stops the plugins for an Ephemery restart, which frees the ports of the listeners they own.
   * Unlike {@link #stopPlugins()}, the registered plugins, with the options parsed into them, and
   * their class loaders are kept, and the context goes back to the registered state, so that {@link
   * #beforeExternalServices()} and {@link #startPlugins()} start the same plugins again.
   */
  public void stopPluginsForRestart() {
    if (state == Lifecycle.BEFORE_MAIN_LOOP_STARTED
        || state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED) {
      stopRegisteredPlugins();
    }
    state = Lifecycle.REGISTERED;
  }

  private void stopRegisteredPlugins() {
    for (final BesuPlugin plugin : registeredPlugins) {
      try {
        plugin.stop();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Stopped plugin of type {}.", plugin.getClass().getName());
        }
      } catch (final Exception e) {
        LOG.error("Error stopping plugin of type " + plugin.getClass().getName(), e);
      }
    }
  }

  private static URL pathToURIOrNull(final Path p) {
    try {
      return p.toUri().toURL();
    } catch (final MalformedURLException e) {
      return null;
    }
  }

  private List<BesuPlugin> detectPlugins(final PluginConfiguration config) {
    final URLClassLoader pluginClassLoader = createPluginClassLoader(config.getPluginsDir());
    if (pluginClassLoader == null) {
      return List.of();
    }
    final var foundPlugins = loadPlugins(pluginClassLoader);
    try {
      PluginVerifier.verify(config.getPluginsVerificationMode(), pluginClassLoader, foundPlugins);
    } catch (final IOException e) {
      LOG.error("Error enumerating plugins, could not load plugins", e);
      return List.of();
    }
    return foundPlugins;
  }

  private List<BesuPlugin> detectBundledPlugins(final PluginConfiguration config) {
    // a separate class loader, so that a plugin installed in the plugins directory can replace a
    // bundled plugin of the same class. Bundled plugins are not verified: they are built
    // separately, against an earlier Besu version, and Besu's build picks the version it bundles
    // (the docker image tests check that the bundled GraphQL plugin serves requests).
    final URLClassLoader classLoader = createPluginClassLoader(config.getBundledPluginsDir());
    if (classLoader == null) {
      return List.of();
    }
    // only the plugins in the bundled plugins directory, not those on Besu's own class path
    return loadPlugins(classLoader).stream()
        .filter(plugin -> plugin.getClass().getClassLoader() == classLoader)
        .toList();
  }

  private static List<BesuPlugin> loadPlugins(final ClassLoader classLoader) {
    return StreamSupport.stream(
            ServiceLoader.load(BesuPlugin.class, classLoader).spliterator(), false)
        .toList();
  }

  private URLClassLoader createPluginClassLoader(final Path pluginsDir) {
    if (pluginsDir == null || !pluginsDir.toFile().isDirectory()) {
      LOG.debug("Plugin directory does not exist, skipping registration. - {}", pluginsDir);
      return null;
    }
    LOG.debug("Searching for plugins in {}", pluginsDir.toAbsolutePath());
    try (final Stream<Path> pluginFilesList = Files.list(pluginsDir)) {
      final URL[] pluginJarURLs =
          pluginFilesList
              .filter(p -> p.getFileName().toString().endsWith(".jar"))
              .map(BesuPluginContextImpl::pathToURIOrNull)
              .toArray(URL[]::new);
      // The URLClassLoader must remain open for the entire application lifecycle
      // as plugins may load classes lazily during their operation. The classloader
      // will be closed in stopPlugins() during shutdown.
      final URLClassLoader classLoader =
          new URLClassLoader(pluginJarURLs, this.getClass().getClassLoader());
      pluginClassLoaders.add(classLoader);
      return classLoader;
    } catch (final IOException e) {
      LOG.error("Error enumerating plugins, could not load plugins", e);
      return null;
    }
  }

  @Override
  public Map<String, String> getPluginVersions() {
    return Collections.unmodifiableMap(pluginVersions);
  }

  /**
   * Gets plugins.
   *
   * @return the plugins
   */
  @VisibleForTesting
  List<BesuPlugin> getRegisteredPlugins() {
    return Collections.unmodifiableList(registeredPlugins);
  }

  /**
   * Gets plugins by name.
   *
   * @return the plugins by name
   */
  public Map<String, BesuPlugin> getPluginsByName() {
    return registeredPlugins.stream()
        .collect(Collectors.toMap(plugin -> plugin.getName(), plugin -> plugin));
  }

  /**
   * Generates a summary log of plugin registration. The summary includes registered plugins,
   * detected but not registered (skipped) plugins
   *
   * @return A list of strings, each representing a line in the summary log.
   */
  public List<String> getPluginsSummaryLog() {
    List<String> summary = new ArrayList<>();
    summary.add("Plugin Registration Summary:");

    // Log registered plugins with their names and versions
    if (registeredPlugins.isEmpty()) {
      summary.add("No plugins have been registered.");
    } else {
      summary.add("Registered Plugins:");
      registeredPlugins.forEach(
          plugin ->
              summary.add(
                  String.format(
                      " - %s (%s)", plugin.getClass().getSimpleName(), plugin.getVersion())));
    }

    // Identify and log detected but not registered (skipped) plugins
    List<BesuPlugin> skippedPlugins =
        detectedPlugins.stream().filter(plugin -> !registeredPlugins.contains(plugin)).toList();

    if (!skippedPlugins.isEmpty()) {
      summary.add("Detected but not registered:");
      skippedPlugins.forEach(
          plugin ->
              summary.add(
                  String.format(
                      " - %s (%s)", plugin.getClass().getSimpleName(), plugin.getVersion())));
    }
    summary.add(
        String.format(
            "TOTAL = %d of %d plugins successfully registered.",
            registeredPlugins.size(), detectedPlugins.size()));

    return summary;
  }

  /**
   * Gets the simple class names of the registered plugins that are bundled with Besu.
   *
   * @return the names of the registered bundled plugins
   */
  public Set<String> getRegisteredBundledPlugins() {
    return bundledPlugins.stream()
        .filter(registeredPlugins::contains)
        .map(plugin -> plugin.getClass().getSimpleName())
        .collect(toSet());
  }
}
