/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.HashMap;

import kotlin.collections.ArrayDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class SnapServer {

  private final EthMessages snapMessages;
  private final WorldStateArchive worldStateArchive;

  private final static Logger LOG = LoggerFactory.getLogger(SnapServer.class);

  private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;

  public SnapServer(final EthMessages snapMessages, final WorldStateArchive worldStateArchive) {
    this.snapMessages = snapMessages;
    this.worldStateArchive = worldStateArchive;
    this.registerResponseConstructors();
  }

  private void registerResponseConstructors() {
    snapMessages.registerResponseConstructor(
        SnapV1.GET_ACCOUNT_RANGE,
        messageData -> constructGetAccountRangeResponse(worldStateArchive, messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_STORAGE_RANGE,
        messageData -> constructGetStorageRangeResponse(worldStateArchive, messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_BYTECODES,
        messageData -> constructGetBytecodesResponse(worldStateArchive, messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_TRIE_NODES,
        messageData -> constructGetTrieNodesResponse(worldStateArchive, messageData));
  }

  private MessageData constructGetAccountRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return AccountRangeMessage.create(new HashMap<>(), new ArrayDeque<>());
  }

  private MessageData constructGetStorageRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return StorageRangeMessage.create(new ArrayDeque<>(), new ArrayDeque<>());
  }

  private MessageData constructGetBytecodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return ByteCodesMessage.create(new ArrayDeque<>());
  }

  private MessageData constructGetTrieNodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    final GetTrieNodesMessage getTrieNodes = GetTrieNodesMessage.readFrom(message);
    final GetTrieNodesMessage.TrieNodesPaths paths = getTrieNodes.paths(true);
    final BonsaiPersistedWorldState worldState =
        (BonsaiPersistedWorldState) worldStateArchive.getMutable();

    final int maxResponseBytes = Math.min(paths.responseBytes().intValue(), MAX_RESPONSE_SIZE);
    final AtomicInteger currentResponseSize = new AtomicInteger();

    LOG.info("Receive get trie nodes range message");

    final ArrayList<Bytes> trieNodes = new ArrayList<>();
    final Iterator<List<Bytes>> pathsIterator = paths.paths().iterator();

    final MerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            (location, key) ->
                worldState.getWorldStateStorage().getAccountStateTrieNode(location, key),
            paths.worldStateRootHash(),
            Function.identity(),
            Function.identity());

    while (pathsIterator.hasNext()) {
      List<Bytes> pathset = pathsIterator.next();
      if (pathset.size() == 1) {
        accountTrie
            .getNodeWithPath(CompactEncoding.decode(pathset.get(0)))
            .map(Node::getRlp)
            .ifPresent(
                rlp -> {
                  trieNodes.add(rlp);
                  currentResponseSize.addAndGet(rlp.size());
                });
      } else {
        final Hash accountHash = Hash.wrap(Bytes32.wrap(pathset.get(0)));
        for (int i = 1; i < pathset.size(); i++) {
          final MerklePatriciaTrie<Bytes, Bytes> storageTrie =
              new StoredMerklePatriciaTrie<>(
                  (location, key) ->
                      worldState
                          .getWorldStateStorage()
                          .getAccountStorageTrieNode(accountHash, location, key),
                  paths.worldStateRootHash(),
                  Function.identity(),
                  Function.identity());
          storageTrie
              .getNodeWithPath(CompactEncoding.decode(pathset.get(i)))
              .map(Node::getRlp)
              .ifPresent(
                  rlp -> {
                    trieNodes.add(rlp);
                    currentResponseSize.addAndGet(rlp.size());
                  });
        }
      }
      if (currentResponseSize.get() > maxResponseBytes) {
        break;
      }
    }
    return TrieNodes.create(trieNodes);
  }
}
