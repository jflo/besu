package org.hyperledger.besu.config.experimental;

import dagger.Component;

@Component(modules = {MergeOptions.class})
public interface MergeConfigurationFactory {
    MergeConfiguration mergeConfiguration();
}
