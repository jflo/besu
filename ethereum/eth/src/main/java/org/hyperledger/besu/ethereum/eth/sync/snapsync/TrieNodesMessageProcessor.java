/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieNodesMessageProcessor implements EthMessages.MessageCallback {

  private final WorldStateArchive archive;

  private static final Logger LOG = LoggerFactory.getLogger(TrieNodesMessageProcessor.class);

  public TrieNodesMessageProcessor(WorldStateArchive archive) {
    this.archive = archive;
  }

  @Override
  public void exec(EthMessage message) {

  }
}
