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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.ACCEPTED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.BlockValidationResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine.NewPayloadParameterV1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockBodyBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEngineNewPayload extends ExecutionEngineJsonRpcMethod {

  protected static final Hash OMMERS_HASH_CONSTANT = Hash.EMPTY_LIST_HASH;
  private static final Logger LOG = LoggerFactory.getLogger(AbstractEngineNewPayload.class);
  protected static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final MergeMiningCoordinator mergeCoordinator;
  private final EthPeers ethPeers;

  public AbstractEngineNewPayload(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, engineCallListener);
    this.mergeCoordinator = mergeCoordinator;
    this.ethPeers = ethPeers;
  }

  protected abstract <P extends NewPayloadParameterV1> P parseVersionedParam(final JsonRpcRequestContext request);

  @Override
  public final JsonRpcResponse response(final JsonRpcRequestContext request) {
    var blockParam = parseVersionedParam(request);
    final BlockIdentifier blockId = new BlockIdentifier(blockParam.getBlockNumber(), blockParam.getBlockHash(), blockParam.getParentHash());
    final CompletableFuture<JsonRpcResponse> cf = new CompletableFuture<>();
    final Optional<Hash> latestValidHash = mergeCoordinator.getLatestValidAncestor(Hash.wrap(blockId.parentHash()));

    syncVertx.<JsonRpcResponse>executeBlocking(
            z -> {
              LOG.trace(
                      "execution engine JSON-RPC request {} {}",
                      this.getName(),
                      request.getRequest().getParams());
              z.tryComplete(syncResponse(request));
            },
            true,
            resp ->
                    cf.complete(
                            resp.otherwise(
                                            t -> {
                                              if (LOG.isDebugEnabled()) {
                                                LOG.atDebug()
                                                        .setMessage("failed to exec consensus method {}")
                                                        .addArgument(this.getName())
                                                        .setCause(t)
                                                        .log();
                                              } else {
                                                LOG.atError()
                                                        .setMessage("failed to exec consensus method {}, error: {}")
                                                        .addArgument(this.getName())
                                                        .addArgument(t.getMessage())
                                                        .log();
                                              }
                                              if("argument cannot be null".equals(t.getMessage())) {
                                                return respondWithInvalid(
                                                        request.getRequest().getId(),
                                                        blockId,
                                                        latestValidHash.orElse(Hash.EMPTY),
                                                        INVALID,
                                                        t.getMessage());
                                              } else {
                                                return new JsonRpcErrorResponse(
                                                        request.getRequest().getId(), RpcErrorType.INVALID_REQUEST);
                                              }

                                            })
                                    .result()));
    try {
      return cf.get();
    } catch (InterruptedException e) {
      LOG.error("Failed to get execution engine response", e);
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.TIMEOUT_ERROR);
    } catch (ExecutionException e) {
      LOG.error("Failed to get execution engine response", e);
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INTERNAL_ERROR);
    }
  }


  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    engineCallListener.executionEngineCalled();
    final Object reqId = requestContext.getRequest().getId();
    var newPayloadParam = parseVersionedParam(requestContext);
    final ValidationResult<RpcErrorType> parameterValidationResult =
            validateRequest(newPayloadParam, requestContext);

    if (!parameterValidationResult.isValid()) {
      return new JsonRpcErrorResponse(reqId, parameterValidationResult);
    }

    BlockIdentifier blockId =
            new BlockIdentifier(
                    newPayloadParam.getBlockNumber(),
                    newPayloadParam.getBlockHash(),
                    newPayloadParam.getParentHash());

    Hash latestValidHash = mergeCoordinator.getLatestValidAncestor(Hash.wrap(blockId.parentHash())).orElse(Hash.ZERO);


    final EngineBlockValidationResult blockValidationResult = validateBlock(newPayloadParam);
    if(blockValidationResult.status().equals(INVALID)) {
      return respondWithInvalid(
              reqId,
              blockId,
              latestValidHash,
              INVALID,
              blockValidationResult.validationResult().toString());
    }

    BlockBody allegedBody;
    try {
      allegedBody = composeBody(requestContext).build();
    } catch(RLPException rlpE) {
        return respondWithInvalid(
                reqId,
                blockId,
                latestValidHash,
                INVALID,
                "Failed to parse block body: "+rlpE.getMessage());
    }
    Hash txRoot = BodyValidation.transactionsRoot(allegedBody.getTransactions());
    BlockHeader allegedHeader = composeNewHeader(requestContext, newPayloadParam, txRoot).buildBlockHeader();

    Block allegedBlock = new Block(allegedHeader, allegedBody);

    final ValidationResult<RpcErrorType> forkValidationResult =
        validateForkSupported(allegedBlock.getHeader().getTimestamp());
    if (!forkValidationResult.isValid()) {
      return new JsonRpcErrorResponse(reqId, forkValidationResult);
    }

    final Optional<BlockHeader> maybeParentHeader =
        protocolContext.getBlockchain().getBlockHeader(allegedBlock.getHeader().getParentHash());

    LOG.atTrace()
        .setMessage("newPayloadHeader: {}")
        .addArgument(() -> Json.encodePrettily(allegedBlock.getHeader()))
        .log();

    if (mergeContext.get().isSyncing()) {
      LOG.debug("We are syncing");
      return respondWith(reqId, blockId, null, SYNCING);
    }

    // ensure the block hash matches the blockParam hash
    // this must be done before other block validity checks
    if (!allegedHeader.getHash().equals(blockId.blockHash())) {
      String errorMessage =
          String.format(
              "Computed block hash %s does not match block hash parameter %s",
              allegedHeader.getBlockHash(), blockId.blockHash());
      LOG.debug(errorMessage);
      return respondWithInvalid(reqId, blockId, latestValidHash, getInvalidBlockHashStatus(), errorMessage);
    }

    // do we already have this payload
    if (protocolContext.getBlockchain().getBlockByHash(allegedHeader.getBlockHash()).isPresent()) {
      LOG.debug("block already present");
      return respondWith(reqId, blockId, allegedHeader.getBlockHash(), VALID);
    }

    if (mergeCoordinator.isBadBlock(allegedHeader.getBlockHash())) {
      return respondWithInvalid(
          reqId,
          blockId,
          mergeCoordinator
              .getLatestValidHashOfBadBlock(allegedHeader.getBlockHash())
              .orElse(Hash.ZERO),
          INVALID,
          "Block already present in bad block manager.");
    }

    if (maybeParentHeader.isPresent()
        && (allegedHeader.getTimestamp() <= maybeParentHeader.get().getTimestamp())) {
      return respondWithInvalid(
          reqId,
          blockId,
          mergeCoordinator.getLatestValidAncestor(allegedHeader.getParentHash()).orElse(null),
          INVALID,
          "block timestamp not greater than parent");
    }

    ValidationResult<RpcErrorType> blobValidationResult =
        validateBlobs(
            allegedBlock,
            maybeParentHeader,
            requestContext,
            protocolSchedule.get().getByBlockHeader(allegedBlock.getHeader()));
    if (!blobValidationResult.isValid()) {
      return respondWithInvalid(
          reqId,
          blockId,
          null,
          INVALID,
          blobValidationResult.getErrorMessage());
    }

    if (maybeParentHeader.isEmpty()) {
      LOG.atDebug()
          .setMessage("Parent of block {} is not present, append it to backward sync")
          .addArgument(allegedBlock::toLogString)
          .log();
      mergeCoordinator.appendNewPayloadToSync(allegedBlock);
      return respondWith(reqId, blockId, null, SYNCING);
    }

    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(allegedHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, blockId, null, ACCEPTED);
    }

    // execute block and return result response
    final long startTimeMs = System.currentTimeMillis();
    final BlockProcessingResult executionResult = mergeCoordinator.rememberBlock(allegedBlock);

    if (executionResult.isSuccessful()) {
      logImportedBlockInfo(allegedBlock, (System.currentTimeMillis() - startTimeMs) / 1000.0);
      return respondWith(reqId, blockId, allegedHeader.getHash(), VALID);
    } else {
      if (executionResult.causedBy().isPresent()) {
        Throwable causedBy = executionResult.causedBy().get();
        if (causedBy instanceof StorageException || causedBy instanceof MerkleTrieException) {
          RpcErrorType error = RpcErrorType.INTERNAL_ERROR;
          JsonRpcErrorResponse response = new JsonRpcErrorResponse(reqId, error);
          return response;
        }
      }
      LOG.debug("New payload is invalid: {}", executionResult.errorMessage.get());
      return respondWithInvalid(
          reqId, blockId, latestValidAncestor.get(), INVALID, executionResult.errorMessage.get());
    }
  }

  JsonRpcResponse respondWith(
      final Object requestId,
      final BlockIdentifier param,
      final Hash latestValidHash,
      final EngineStatus status) {
    if (INVALID.equals(status) || INVALID_BLOCK_HASH.equals(status)) {
      throw new IllegalArgumentException(
          "Don't call respondWith() with invalid status of " + status.toString());
    }
    LOG.atDebug()
        .setMessage(
            "New payload: number: {}, hash: {}, parentHash: {}, latestValidHash: {}, status: {}")
        .addArgument(Long.toUnsignedString(param.number()))
        .addArgument(param.blockHash())
        .addArgument(param.parentHash())
        .addArgument(() -> latestValidHash == null ? null : latestValidHash.toHexString())
        .addArgument(status::name)
        .log();
    return new JsonRpcSuccessResponse(
        requestId, new EnginePayloadStatusResult(status, latestValidHash, Optional.empty()));
  }

  protected EngineStatus getInvalidBlockHashStatus() {
    return INVALID;
  }

  protected  abstract <P extends NewPayloadParameterV1> ValidationResult<RpcErrorType> validateRequest( final P newPayloadParam,
      final JsonRpcRequestContext requestContext);

  public record EngineBlockValidationResult(EngineStatus status, BlockValidationResult validationResult) {};
  protected abstract <P extends NewPayloadParameterV1> EngineBlockValidationResult validateBlock(P payload);

  protected abstract <P extends NewPayloadParameterV1> BlockHeaderBuilder composeNewHeader(
      final JsonRpcRequestContext requestContext, final  P newPayloadParam, final Hash txroot);

  protected abstract ValidationResult<RpcErrorType> validateBlobs(
      final Block newBlock,
      final Optional<BlockHeader> maybeParentHeader,
      final JsonRpcRequestContext requestContext,
      final ProtocolSpec protocolSpec);

  protected abstract BlockBodyBuilder composeBody(final JsonRpcRequestContext requestContext);

  private void logImportedBlockInfo(final Block block, final double timeInS) {
    final StringBuilder message = new StringBuilder();
    message.append("Imported #%,d / %d tx");
    final List<Object> messageArgs =
        new ArrayList<>(
            List.of(block.getHeader().getNumber(), block.getBody().getTransactions().size()));
    if (block.getBody().getWithdrawals().isPresent()) {
      message.append(" / %d ws");
      messageArgs.add(block.getBody().getWithdrawals().get().size());
    }
    if (block.getBody().getDeposits().isPresent()) {
      message.append(" / %d ds");
      messageArgs.add(block.getBody().getDeposits().get().size());
    }
    message.append(" / base fee %s / %,d (%01.1f%%) gas / (%s) in %01.3fs. Peers: %d");
    messageArgs.addAll(
        List.of(
            block.getHeader().getBaseFee().map(Wei::toHumanReadableString).orElse("N/A"),
            block.getHeader().getGasUsed(),
            (block.getHeader().getGasUsed() * 100.0) / block.getHeader().getGasLimit(),
            block.getHash().toHexString(),
            timeInS,
            ethPeers.peerCount()));
    LOG.info(String.format(message.toString(), messageArgs.toArray()));
  }
}
