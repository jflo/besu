package org.hyperledger.besu.cli.options.unstable;

import dagger.Component;

@Component()
public interface MergeOptionsFactory {
    MergeOptions mergeOptions();
}
