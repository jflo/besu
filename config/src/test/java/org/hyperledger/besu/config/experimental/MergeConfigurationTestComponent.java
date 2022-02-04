package org.hyperledger.besu.config.experimental;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = {MergeConfigurationProvider.class})
@Singleton
public interface MergeConfigurationTestComponent extends MergeConfigurationComponent {
    @Singleton
    void inject(MergeConfigurationTest test);
}
