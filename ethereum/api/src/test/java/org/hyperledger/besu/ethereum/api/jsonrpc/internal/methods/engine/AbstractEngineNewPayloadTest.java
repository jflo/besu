/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.ACCEPTED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractEngineNewPayloadTest extends AbstractScheduledApiTest {

  @FunctionalInterface
  interface MethodFactory {
    AbstractEngineNewPayload create(
        final Vertx vertx,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final MergeMiningCoordinator mergeCoordinator,
        final EthPeers ethPeers,
        final EngineCallListener engineCallListener);
  }

  protected AbstractEngineNewPayload method;
  protected Optional<Bytes32> maybeParentBeaconBlockRoot = Optional.empty();

  public AbstractEngineNewPayloadTest() {}

  protected static final Vertx vertx = Vertx.vertx();
  protected static final Hash mockHash = Hash.hash(Bytes32.fromHexStringLenient("0x1337deadbeef"));

  @Mock protected ProtocolSpec protocolSpec;

  @Mock protected ProtocolContext protocolContext;

  @Mock protected MergeContext mergeContext;

  @Mock protected MergeMiningCoordinator mergeCoordinator;

  @Mock protected MutableBlockchain blockchain;

  @Mock protected EthPeers ethPeers;

  @Mock protected EngineCallListener engineCallListener;

  @BeforeEach
  @Override
  public void before() {
    super.before();
    when(protocolContext.safeConsensusContext(Mockito.any())).thenReturn(Optional.of(mergeContext));
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    lenient()
        .when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    lenient()
        .when(protocolSpec.getDepositsValidator())
        .thenReturn(new DepositsValidator.ProhibitedDeposits());
    lenient().when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    lenient().when(ethPeers.peerCount()).thenReturn(1);
  }

  @Test
  public abstract void shouldReturnExpectedMethodName();

  @Test
  public void shouldReturnValid() {
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
                Optional.of(Collections.emptyList()),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidOnBlockExecutionError() {
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult("error 42"), Optional.of(Collections.emptyList()), Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHash);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("error 42");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnAcceptedOnLatestValidAncestorEmpty() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.empty());

    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(ACCEPTED.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnSuccessOnAlreadyPresent() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    Block mockBlock =
        new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));

    when(blockchain.getBlockByHash(any())).thenReturn(Optional.of(mockBlock));

    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    assertValidResponse(mockHeader, resp);
  }

  @Test
  public void shouldReturnInvalidWithLatestValidHashIsABadBlock() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    Hash latestValidHash = Hash.hash(Bytes32.fromHexStringLenient("0xcafebabe"));

    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(mergeCoordinator.isBadBlock(mockHeader.getHash())).thenReturn(true);
    when(mergeCoordinator.getLatestValidHashOfBadBlock(mockHeader.getHash()))
        .thenReturn(Optional.of(latestValidHash));

    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEqualTo(Optional.of(latestValidHash));
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldNotReturnInvalidOnStorageException() {
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.empty(), new StorageException("database bedlam")),
                Optional.of(Collections.emptyList()),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    fromErrorResp(resp);
    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, times(0)).addBadBlock(any(), any());
    // verify mainnetBlockValidator does not add to bad block manager
  }

  @Test
  public void shouldNotReturnInvalidOnHandledMerkleTrieException() {
    //List<String> txs = Collections.emptyList();
    BlockHeader toAdd =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.empty(), new MerkleTrieException("missing leaf")),
            Optional.of(Collections.emptyList()),
            Optional.empty());

    //Computed block hash 0xdcc705e2b540cd869d5c01c28d820fafbb3d8ba3f3e1222af4d3c27e28843a16 does not match
    // block hash parameter 0x74f6609afe157ba1cc189daf116151df7151da1567017ed34a81f6afd0b236ac

    lenient()
        .when(blockchain.getBlockHeader(toAdd.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = respondTo(new Object[] {createNewPayloadParam(toAdd, Collections.emptyList())});

    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, times(0)).addBadBlock(any(), any());

    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    assertThat(((JsonRpcErrorResponse)resp).getErrorType()).isEqualTo(RpcErrorType.INTERNAL_ERROR);
  }

  @Test
  public void shouldNotReturnInvalidOnThrownMerkleTrieException() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.of(mockHash));
    when(mergeCoordinator.rememberBlock(any())).thenThrow(new MerkleTrieException("missing leaf"));

    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    verify(engineCallListener, times(1)).executionEngineCalled();
    verify(mergeCoordinator, never()).addBadBlock(any(), any());

    fromErrorResp(resp);
  }

  @Test
  public void shouldReturnInvalidBlockHashOnBadHashParameter() {
    BlockHeader mockHeader = spy(createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader());
    lenient()
        .when(mergeCoordinator.getLatestValidAncestor(mockHeader.getBlockHash()))
        .thenReturn(Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    lenient().when(mockHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getStatusAsString()).isEqualTo(getExpectedInvalidBlockHashStatus().name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldCheckBlockValidityBeforeCheckingByHashForExisting() {
    BlockHeader realHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    BlockHeader paramHeader = spy(realHeader);
    when(paramHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));

    var resp =
        respondTo(new Object[] {createNewPayloadParam(paramHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    //assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(getExpectedInvalidBlockHashStatus().name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidOnMalformedTransactions() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    when(mergeCoordinator.getLatestValidAncestor(any(Hash.class)))
        .thenReturn(Optional.of(mockHash));

    var resp =
        respondTo(new Object[] {createNewPayloadParam(mockHeader, List.of("0xDEAD", "0xBEEF"))});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHash);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldRespondWithSyncingDuringForwardSync() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    when(mergeContext.isSyncing()).thenReturn(Boolean.TRUE);
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getError()).isNull();
    assertThat(res.getStatusAsString()).isEqualTo(SYNCING.name());
    assertThat(res.getLatestValidHash()).isEmpty();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldRespondWithSyncingDuringBackwardsSync() {
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    when(mergeCoordinator.appendNewPayloadToSync(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).isEmpty();
    assertThat(res.getStatusAsString()).isEqualTo(SYNCING.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldRespondWithInvalidIfExtraDataIsNull() throws IOException {
    BlockHeader realHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    BlockHeader paramHeader = spy(realHeader);
    when(paramHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));
    ObjectMapper mapper = new ObjectMapper();
    String paramJson = createNewPayloadParam(paramHeader, Collections.emptyList());
    JsonNode paramNode = mapper.createParser(paramJson).readValueAsTree();
    ((ObjectNode) paramNode).put("extraData", (String) null);
    String nulledOutExtraData = mapper.writeValueAsString(paramNode);
    var resp =
        respondTo(new Object[] {nulledOutExtraData});

    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnInvalidWhenBadBlock() {
    when(mergeCoordinator.isBadBlock(any(Hash.class))).thenReturn(true);
    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),Optional.of(Collections.emptyList()), Optional.empty()).buildHeader();
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());
    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash()).contains(Hash.ZERO);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Block already present in bad block manager.");
    verify(engineCallListener, times(1)).executionEngineCalled();
  }

  @Test
  public void shouldReturnValidIfProtocolScheduleIsEmpty() {
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(null);
    BlockHeader mockHeader =
        prepChainForAddingNewBlock(
            new BlockProcessingResult(Optional.of(new BlockProcessingOutputs(null, List.of()))),
            Optional.of(Collections.emptyList()),
            Optional.empty());
    lenient()
        .when(blockchain.getBlockHeader(mockHeader.getParentHash()))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    var resp = respondTo(new Object[] {createNewPayloadParam(mockHeader, Collections.emptyList())});

    assertValidResponse(mockHeader, resp);
  }

  protected JsonRpcResponse respondTo(final Object[] params) {
    return method.response(
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", this.method.getName(), params)));
  }

  abstract protected String createNewPayloadParam(
      final BlockHeader header, final List<String> txs);

  protected BlockHeader prepChainForAddingNewBlock(
      final BlockProcessingResult value,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<List<Deposit>> maybeDeposits) {

    BlockHeader mockHeader = createBlockHeaderTestFixture(Collections.emptyList(),maybeWithdrawals, maybeDeposits).buildHeader();
    when(blockchain.getBlockByHash(mockHeader.getHash())).thenReturn(Optional.empty());//make sure it's not there already
    when(mergeCoordinator.getLatestValidAncestor(any(BlockHeader.class)))
        .thenReturn(Optional.of(mockHash));
    when(mergeCoordinator.rememberBlock(any())).thenReturn(value);
    return mockHeader;
  }

  protected ExecutionEngineJsonRpcMethod.EngineStatus getExpectedInvalidBlockHashStatus() {
    return INVALID;
  }

  protected EnginePayloadStatusResult fromSuccessResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    return Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .map(JsonRpcSuccessResponse::getResult)
        .map(EnginePayloadStatusResult.class::cast)
        .get();
  }

  protected JsonRpcError fromErrorResp(final JsonRpcResponse resp) {
    assertThat(resp.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    return Optional.of(resp)
        .map(JsonRpcErrorResponse.class::cast)
        .map(JsonRpcErrorResponse::getError)
        .get();
  }

  protected abstract BlockHeaderTestFixture createBlockHeaderTestFixture(
          List<Transaction> maybeTransactions,
          Optional<List<Withdrawal>> maybeWithdrawals,
          Optional<List<Deposit>> maybeDeposits);

  protected void assertValidResponse(final BlockHeader mockHeader, final JsonRpcResponse resp) {
    EnginePayloadStatusResult res = fromSuccessResp(resp);
    assertThat(res.getLatestValidHash().get()).isEqualTo(mockHeader.getHash());
    assertThat(res.getStatusAsString()).isEqualTo(VALID.name());
    assertThat(res.getError()).isNull();
    verify(engineCallListener, times(1)).executionEngineCalled();
  }
}
