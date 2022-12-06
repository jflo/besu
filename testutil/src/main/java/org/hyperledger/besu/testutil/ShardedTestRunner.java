/*
 * Copyright 2022 Hyperledger Besu Contributors
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
package org.hyperledger.besu.testutil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * Sharded test executor, inspired from <a
 * href="https://github.com/alsutton/sharded-test-executor">alsutton/sharded-test-executor</a>
 */
public class ShardedTestRunner extends BlockJUnit4ClassRunner {

  private static final MessageDigest digest;

  private static final long totalShardCount;

  private static final long thisShard;

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  private static MessageDigest setUpDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  static {
    digest = setUpDigest();

    totalShardCount = getValue("SHARD_COUNT");
    if (totalShardCount == 1L) {
      thisShard = 0;
    } else {
      thisShard = getValue("SHARD_INDEX");
    }
  }

  private static Long getValue(final String value) {
    String shardCount = System.getProperty(value, System.getenv(value));
    Long candidate = null;
    if (shardCount != null) {
      try {
        candidate = Long.parseLong(shardCount);
      } catch (Throwable t) {
        candidate = 1L;
      }
    }
    if (candidate == null) {
      candidate = 1L;
    }
    return candidate;
  }

  private final String className;

  public ShardedTestRunner(final Class<?> klass) throws InitializationError {
    super(klass);
    className = klass.getCanonicalName();
  }

  @Override
  public void run(final RunNotifier notifier) {
    Bytes hash = Bytes.wrap(digest.digest(className.getBytes(StandardCharsets.UTF_8)));
    int targetShard = UInt256.fromBytes(Bytes32.leftPad(hash)).mod(totalShardCount).intValue();
    if (thisShard == targetShard) {
      super.run(notifier);
    } else {
      notifier.fireTestIgnored(getDescription());
    }
  }
}
