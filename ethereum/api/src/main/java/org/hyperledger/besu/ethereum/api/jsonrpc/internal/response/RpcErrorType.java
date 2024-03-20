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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.response;

import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

public enum RpcErrorType implements RpcMethodError {
  // Standard errors
  PARSE_ERROR(-32700, "Parse error"),
  INVALID_REQUEST(-32600, "Invalid Request"),
  METHOD_NOT_FOUND(-32601, "Method not found"),
  INVALID_PARAMS(-32602, "Invalid params"),
  INTERNAL_ERROR(-32603, "Internal error"),
  TIMEOUT_ERROR(-32603, "Timeout expired"),

  METHOD_NOT_ENABLED(-32604, "Method not enabled"),

  // Resource unavailable error
  TX_POOL_DISABLED(-32002, "Transaction pool not enabled"),

  // eth_getBlockByNumber specific error message
  UNKNOWN_BLOCK(-39001, "Unknown block"),

  // eth_sendTransaction specific error message
  ETH_SEND_TX_NOT_AVAILABLE(
      -32604,
      "The method eth_sendTransaction is not supported. Use eth_sendRawTransaction to send a signed transaction to Besu."),
  ETH_SEND_TX_ALREADY_KNOWN(-32000, "Known transaction"),
  ETH_SEND_TX_REPLACEMENT_UNDERPRICED(-32000, "Replacement transaction underpriced"),
  // P2P related errors
  P2P_DISABLED(-32000, "P2P has been disabled. This functionality is not available"),
  P2P_NETWORK_NOT_RUNNING(-32000, "P2P network is not running"),

  // Filter & Subscription Errors
  FILTER_NOT_FOUND(-32000, "Filter not found"),
  LOGS_FILTER_NOT_FOUND(-32000, "Logs filter not found"),
  SUBSCRIPTION_NOT_FOUND(-32000, "Subscription not found"),
  NO_MINING_WORK_FOUND(-32000, "No mining work available yet"),

  // Transaction validation failures
  NONCE_TOO_LOW(-32001, "Nonce too low"),
  INVALID_TRANSACTION_SIGNATURE(-32002, "Invalid signature"),
  INVALID_TRANSACTION_TYPE(-32602, "Invalid transaction type"),
  INTRINSIC_GAS_EXCEEDS_LIMIT(-32003, "Intrinsic gas exceeds gas limit"),
  TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE(-32004, "Upfront cost exceeds account balance"),
  EXCEEDS_BLOCK_GAS_LIMIT(-32005, "Transaction gas limit exceeds block gas limit"),
  EXCEEDS_RPC_MAX_BLOCK_RANGE(-32005, "Requested range exceeds maximum RPC range limit"),
  EXCEEDS_RPC_MAX_BATCH_SIZE(-32005, "Number of requests exceeds max batch size"),
  NONCE_TOO_HIGH(-32006, "Nonce too high"),
  TX_SENDER_NOT_AUTHORIZED(-32007, "Sender account not authorized to send transactions"),
  CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE(-32008, "Initial sync is still in progress"),
  GAS_PRICE_TOO_LOW(-32009, "Gas price below configured minimum gas price"),
  GAS_PRICE_BELOW_CURRENT_BASE_FEE(-32009, "Gas price below current base fee"),

  BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE(-32009, "blob gas price below current blob base fee"),
  WRONG_CHAIN_ID(-32000, "Wrong chainId"),
  REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED(-32000, "ChainId not supported"),
  REPLAY_PROTECTED_SIGNATURE_REQUIRED(-32000, "ChainId is required"),
  TX_FEECAP_EXCEEDED(-32000, "Transaction fee cap exceeded"),
  REVERT_ERROR(
      -32000,
      "Execution reverted",
      data -> JsonRpcErrorResponse.decodeRevertReason(Bytes.fromHexString(data))),
  TRANSACTION_NOT_FOUND(-32000, "Transaction not found"),
  MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS(
      -32000, "Max priority fee per gas exceeds max fee per gas"),
  NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER(
      -32000, "Transaction nonce is too distant from current sender nonce"),
  LOWER_NONCE_INVALID_TRANSACTION_EXISTS(
      -32000, "An invalid transaction with a lower nonce exists"),
  TOTAL_BLOB_GAS_TOO_HIGH(-32000, "Total blob gas too high"),
  PLUGIN_TX_VALIDATOR(-32000, "Plugin has marked the transaction as invalid"),
  EXECUTION_HALTED(-32000, "Transaction processing could not be completed due to an exception"),

  // Execution engine failures
  UNKNOWN_PAYLOAD(-32001, "Payload does not exist / is not available"),
  INVALID_TERMINAL_BLOCK(-32002, "Terminal block doesn't satisfy terminal block conditions"),
  INVALID_FORKCHOICE_STATE(-38002, "Invalid forkchoice state"),
  INVALID_PAYLOAD_ATTRIBUTES(-38003, "Invalid payload attributes"),
  INVALID_RANGE_REQUEST_TOO_LARGE(-38004, "Too large request"),
  UNSUPPORTED_FORK(-38005, "Unsupported fork"),
  // Miner failures
  COINBASE_NOT_SET(-32010, "Coinbase not set. Unable to start mining without a coinbase"),
  NO_HASHES_PER_SECOND(-32011, "No hashes being generated by the current node"),
  TARGET_GAS_LIMIT_MODIFICATION_UNSUPPORTED(
      -32011, "The node is not attempting to target a gas limit so the target can't be changed"),

  // Wallet errors
  COINBASE_NOT_SPECIFIED(-32000, "Coinbase must be explicitly specified"),

  // Account errors
  NO_ACCOUNT_FOUND(-32000, "Account not found"),

  // Worldstate errors
  WORLD_STATE_UNAVAILABLE(-32000, "World state unavailable"),

  // Debug failures
  BLOCK_NOT_FOUND(-32000, "Block not found"),
  PARENT_BLOCK_NOT_FOUND(-32000, "Parent block not found"),

  // Permissioning/Account allowlist errors
  ACCOUNT_ALLOWLIST_NOT_ENABLED(-32000, "Account allowlist has not been enabled"),
  ACCOUNT_ALLOWLIST_EMPTY_ENTRY(-32000, "Request contains an empty list of accounts"),
  ACCOUNT_ALLOWLIST_INVALID_ENTRY(-32000, "Request contains an invalid account"),
  ACCOUNT_ALLOWLIST_DUPLICATED_ENTRY(-32000, "Request contains duplicate accounts"),
  ACCOUNT_ALLOWLIST_EXISTING_ENTRY(-32000, "Cannot add an existing account to allowlist"),
  ACCOUNT_ALLOWLIST_ABSENT_ENTRY(-32000, "Cannot remove an absent account from allowlist"),

  // Permissioning/Node allowlist errors
  NODE_ALLOWLIST_NOT_ENABLED(-32000, "Node allowlist has not been enabled"),
  NODE_ALLOWLIST_EMPTY_ENTRY(-32000, "Request contains an empty list of nodes"),
  NODE_ALLOWLIST_INVALID_ENTRY(-32000, "Request contains an invalid node"),
  NODE_ALLOWLIST_DUPLICATED_ENTRY(-32000, "Request contains duplicate nodes"),
  NODE_ALLOWLIST_EXISTING_ENTRY(-32000, "Cannot add an existing node to allowlist"),
  NODE_ALLOWLIST_MISSING_ENTRY(-32000, "Cannot remove an absent node from allowlist"),
  NODE_ALLOWLIST_FIXED_NODE_CANNOT_BE_REMOVED(
      -32000, "Cannot remove a fixed node (bootnode or static node) from allowlist"),

  // Permissioning/persistence errors
  ALLOWLIST_PERSIST_FAILURE(
      -32000, "Unable to persist changes to allowlist configuration file. Changes reverted"),
  ALLOWLIST_FILE_SYNC(
      -32000,
      "The permissioning allowlist configuration file is out of sync.  The changes have been applied, but not persisted to disk"),
  ALLOWLIST_RELOAD_ERROR(
      -32000,
      "Error reloading permissions file. Please use perm_getAccountsAllowlist and perm_getNodesAllowlist to review the current state of the allowlists"),
  PERMISSIONING_NOT_ENABLED(-32000, "Node/Account allowlist has not been enabled"),
  NON_PERMITTED_NODE_CANNOT_BE_ADDED_AS_A_PEER(-32000, "Cannot add a non-permitted node as a peer"),

  // Permissioning/Authorization errors
  UNAUTHORIZED(-40100, "Unauthorized"),

  // Private transaction errors
  ENCLAVE_ERROR(-50100, "Error communicating with enclave"),
  UNSUPPORTED_PRIVATE_TRANSACTION_TYPE(-50100, "Unsupported private transaction type"),
  PRIVACY_NOT_ENABLED(-50100, "Privacy is not enabled"),
  CREATE_PRIVACY_GROUP_ERROR(-50100, "Error creating privacy group"),
  DECODE_ERROR(-50100, "Unable to decode the private signed raw transaction"),
  DELETE_PRIVACY_GROUP_ERROR(-50100, "Error deleting privacy group"),
  FIND_PRIVACY_GROUP_ERROR(-50100, "Error finding privacy group"),
  FIND_FLEXIBLE_PRIVACY_GROUP_ERROR(-50100, "Error finding flexible privacy group"),
  GET_PRIVATE_TRANSACTION_NONCE_ERROR(-50100, "Unable to determine nonce for account in group."),
  OFFCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST(-50100, "Offchain Privacy group does not exist."),
  FLEXIBLE_PRIVACY_GROUP_DOES_NOT_EXIST(-50100, "Flexible Privacy group does not exist."),
  FLEXIBLE_PRIVACY_GROUP_NOT_ENABLED(-50100, "Flexible privacy groups not enabled."),
  OFFCHAIN_PRIVACY_GROUP_NOT_ENABLED(
      -50100, "Offchain privacy group can't be used with Flexible privacy groups enabled."),
  FLEXIBLE_PRIVACY_GROUP_ID_NOT_AVAILABLE(
      -50100, "Private transactions to flexible privacy groups must use privacyGroupId"),
  PMT_FAILED_INTRINSIC_GAS_EXCEEDS_LIMIT(
      -50100,
      "Privacy Marker Transaction failed due to intrinsic gas exceeding the limit. Gas limit used from the Private Transaction."),
  PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY(
      -50100, "Private from does not match enclave public key"),
  VALUE_NOT_ZERO(-50100, "We cannot transfer ether in a private transaction yet."),
  PRIVATE_TRANSACTION_INVALID(-50100, "Private transaction invalid"),
  PRIVATE_TRANSACTION_FAILED(-50100, "Private transaction failed"),

  CANT_CONNECT_TO_LOCAL_PEER(-32100, "Cannot add local node as peer."),
  CANT_RESOLVE_PEER_ENODE_DNS(-32100, "Cannot resolve enode DNS hostname"),
  DNS_NOT_ENABLED(-32100, "Enode DNS support is disabled"),

  // Invalid input errors
  ENODE_ID_INVALID(
      -32000,
      "Invalid node ID: node ID must have exactly 128 hexadecimal characters and should not include any '0x' hex prefix."),
  JSON_RPC_NOT_CANONICAL_ERROR(-32000, "Invalid input"),

  // Enclave errors
  NODE_MISSING_PEER_URL(-50200, "NodeMissingPeerUrl"),
  NODE_PUSHING_TO_PEER(-50200, "NodePushingToPeer"),
  NODE_PROPAGATING_TO_ALL_PEERS(-50200, "NodePropagatingToAllPeers"),
  NO_SENDER_KEY(-50200, "NoSenderKey"),
  INVALID_PAYLOAD(-50200, "InvalidPayload"),
  ENCLAVE_CREATE_KEY_PAIR(-50200, "EnclaveCreateKeyPair"),
  ENCLAVE_DECODE_PUBLIC_KEY(-50200, "EnclaveDecodePublicKey"),
  ENCLAVE_DECRYPT_WRONG_PRIVATE_KEY(-50200, "EnclaveDecryptWrongPrivateKey"),
  ENCLAVE_ENCRYPT_COMBINE_KEYS(-50200, "EnclaveEncryptCombineKeys"),
  ENCLAVE_MISSING_PRIVATE_KEY_PASSWORD(-50200, "EnclaveMissingPrivateKeyPasswords"),
  ENCLAVE_NO_MATCHING_PRIVATE_KEY(-50200, "EnclaveNoMatchingPrivateKey"),
  ENCLAVE_NOT_PAYLOAD_OWNER(-50200, "EnclaveNotPayloadOwner"),
  ENCLAVE_UNSUPPORTED_PRIVATE_KEY_TYPE(-50200, "EnclaveUnsupportedPrivateKeyType"),
  ENCLAVE_STORAGE_DECRYPT(-50200, "EnclaveStorageDecrypt"),
  ENCLAVE_PRIVACY_GROUP_CREATION(-50200, "EnclavePrivacyGroupIdCreation"),
  ENCLAVE_PAYLOAD_NOT_FOUND(-50200, "EnclavePayloadNotFound"),
  CREATE_GROUP_INCLUDE_SELF(-50200, "CreatePrivacyGroupShouldIncludeSelf"),

  // Tessera error codes
  TESSERA_NODE_MISSING_PEER_URL(-50200, "Recipient not found for key:"),
  TESSERA_CREATE_GROUP_INCLUDE_SELF(
      -50200, "The list of members in a privacy group should include self"),

  /** Storing privacy group issue */
  ENCLAVE_UNABLE_STORE_PRIVACY_GROUP(-50200, "PrivacyGroupNotStored"),
  ENCLAVE_UNABLE_DELETE_PRIVACY_GROUP(-50200, "PrivacyGroupNotDeleted"),
  ENCLAVE_UNABLE_PUSH_DELETE_PRIVACY_GROUP(-50200, "PrivacyGroupNotPushed"),
  ENCLAVE_PRIVACY_GROUP_MISSING(-50200, "PrivacyGroupNotFound"),
  ENCLAVE_PRIVACY_QUERY_ERROR(-50200, "PrivacyGroupQueryError"),
  ENCLAVE_KEYS_CANNOT_DECRYPT_PAYLOAD(-50200, "EnclaveKeysCannotDecryptPayload"),
  METHOD_UNIMPLEMENTED(-50200, "MethodUnimplemented"),

  /** Plugins error */
  PLUGIN_NOT_FOUND(-60000, "Plugin not found"),
  PLUGIN_INTERNAL_ERROR(-32603, "Plugin internal error"),

  // Retesteth Errors

  BLOCK_RLP_IMPORT_ERROR(-32000, "Could not decode RLP for Block"),
  BLOCK_IMPORT_ERROR(-32000, "Could not import Block"),

  UNKNOWN(-32603, "Unknown internal error"),

  INVALID_BLOBS(-32603, "blobs failed kzg validation");

  private final int code;
  private final String message;
  private final Function<String, Optional<String>> dataDecoder;

  RpcErrorType(final int code, final String message) {
    this(code, message, null);
  }

  RpcErrorType(
      final int code, final String message, Function<String, Optional<String>> dataDecoder) {
    this.code = code;
    this.message = message;
    this.dataDecoder = dataDecoder;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public Optional<String> decodeData(final String data) {
    return dataDecoder == null ? Optional.empty() : dataDecoder.apply(data);
  }
}
