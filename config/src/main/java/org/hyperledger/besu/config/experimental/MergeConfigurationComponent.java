package org.hyperledger.besu.config.experimental;

import dagger.Component;

import javax.inject.Singleton;


@Component(modules = {MergeConfigurationProvider.class})
@Singleton
public interface MergeConfigurationComponent {
    MergeConfiguration mergeConfiguration();
}
