package org.hyperledger.besu.cli.options.unstable;

import org.junit.Test;

import java.util.Stack;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"JdkObsolete"})
public class MergeOptionsTest {
    @Test
    public void shouldBeEnabledFromCliConsumer() {
        // enable
        MergeOptions config = DaggerMergeOptionsFactory.create().mergeOptions();
        var mockStack = new Stack<String>();
        mockStack.push("true");
        config.consumeParameters(mockStack, null, null);
        assertThat(config.isMergeEnabled()).isTrue();
        assertThat(config.toDomainObject().isMergeEnabled()).isTrue();
    }
}