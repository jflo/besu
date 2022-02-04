package org.hyperledger.besu.config.experimental;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;


@Module
public class MergeConfigurationProvider {
    private MergeConfiguration config = null;
    @Provides
    @Singleton
    MergeConfiguration mergeConfiguration() {
        if(this.config == null) {
            this.config = new MergeConfiguration();
        }
        return this.config;
    }
}
