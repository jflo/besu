# EIP-7805: Inclusion Lists Engine API

This document describes the Engine API methods and configuration options added to Hyperledger Besu for [EIP-7805](https://eips.ethereum.org/EIPS/eip-7805) inclusion list support.

## Overview

Inclusion lists (ILs) are a mechanism for improving Ethereum's censorship resistance. They allow validators to specify a set of transactions that **must** be included in the next block. The execution client enforces these constraints during payload validation.

### Key Concepts

- **Inclusion list**: An ordered list of raw, RLP-encoded transactions that a proposer commits to including.
- **Strict validation**: The payload is rejected (`INCLUSION_LIST_UNSATISFIED`) if IL constraints are violated.
- **Lenient validation**: Violations are logged as warnings but the payload is accepted (`VALID`). Useful for testnets.
- **MAX_BYTES_PER_INCLUSION_LIST**: 8192 bytes (2^13). The maximum total byte size of all transactions in an inclusion list.

### Workflow

1. Consensus client calls `engine_getInclusionListV1` to retrieve candidate IL transactions from the execution client's mempool.
2. Consensus client calls `engine_forkchoiceUpdatedV4` with `inclusionListTransactions` in payload attributes to initiate payload building.
3. Execution client builds the block, prioritizing IL transactions.
4. Consensus client calls `engine_newPayloadV5` with the payload and IL transactions for validation.

---

## Engine API Methods

### engine_getInclusionListV1

Retrieves transactions for an inclusion list based on the execution client's mempool.

**Method**: `engine_getInclusionListV1`

#### Parameters

| Index | Name         | Type   | Description                              |
|-------|-------------|--------|------------------------------------------|
| 0     | `parentHash` | `DATA` (32 bytes) | Hash of the parent block to build upon |

#### Returns

`Array of DATA` - Hex-encoded RLP transaction byte arrays selected for the inclusion list.

Returns an empty array if no suitable transactions are found.

#### Errors

| Code   | Message               | Description                            |
|--------|-----------------------|----------------------------------------|
| -38006 | Unknown parent block  | The specified `parentHash` was not found |

#### Transaction Selection

The default selector:
- Prioritizes transactions by effective gas price (highest first)
- Filters out blob transactions (per EIP-7805 spec)
- Filters transactions below the parent block's base fee
- Validates sequential nonces per sender account
- Stops when `MAX_BYTES_PER_INCLUSION_LIST` (8192 bytes) is reached

#### Example

**Request:**
```json
{
  "jsonrpc": "2.0",
  "method": "engine_getInclusionListV1",
  "params": [
    "0xe0a94c400a80b2b16b10aa1b6a9c8b1e1e8b1f0c1d2e3f4a5b6c7d8e9f0a1b2c"
  ],
  "id": 1
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": [
    "0xf86c0a8502540be400825208944bbeeb066ed09b7aed07bf39eee0460dfa261520880de0b6b3a7640000801ca0f3ae3b1ca64862f1b5c79f9e3c4d2e8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2ea01b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b",
    "0xf86c0b8502540be400825208944bbeeb066ed09b7aed07bf39eee0460dfa261520880de0b6b3a7640000801ba0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2a03d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4"
  ]
}
```

---

### engine_newPayloadV5

Validates a new execution payload, including inclusion list constraints. Extends V4 with a fifth parameter for inclusion list transactions.

**Method**: `engine_newPayloadV5`

#### Parameters

| Index | Name                          | Type             | Description                                      |
|-------|-------------------------------|------------------|--------------------------------------------------|
| 0     | `executionPayload`            | `Object`         | The execution payload object                     |
| 1     | `expectedBlobVersionedHashes` | `Array of DATA`  | Expected blob versioned hashes                   |
| 2     | `parentBeaconBlockRoot`       | `DATA` (32 bytes)| Parent beacon block root                         |
| 3     | `executionRequests`           | `Array of DATA`  | Execution layer requests                         |
| 4     | `inclusionListTransactions`   | `Array of DATA`  | Hex-encoded IL transaction bytes (optional/nullable) |

#### Returns

`PayloadStatusV1` object with:
- `status`: One of `VALID`, `INVALID`, `SYNCING`, `ACCEPTED`, or `INCLUSION_LIST_UNSATISFIED`
- `latestValidHash`: Hash of the most recent valid block (when applicable)
- `validationError`: Descriptive error message (when applicable)

#### New Status Value

| Status                         | Description                                                                 |
|--------------------------------|-----------------------------------------------------------------------------|
| `INCLUSION_LIST_UNSATISFIED`   | The payload does not satisfy the inclusion list constraints (strict mode only) |

In **lenient** mode, IL violations return `VALID` with a logged warning instead.

#### Validation Rules

1. All IL transactions must appear in the payload in the same relative order (interleaved non-IL transactions are allowed).
2. The total byte size of IL transactions must not exceed `MAX_BYTES_PER_INCLUSION_LIST` (8192 bytes).
3. If the IL parameter is null or empty, validation passes (no constraints to enforce).

#### Example

**Request:**
```json
{
  "jsonrpc": "2.0",
  "method": "engine_newPayloadV5",
  "params": [
    {
      "parentHash": "0xe0a94c...",
      "feeRecipient": "0x4bbeeb...",
      "stateRoot": "0xabc123...",
      "receiptsRoot": "0xdef456...",
      "logsBloom": "0x0000...",
      "prevRandao": "0x123abc...",
      "blockNumber": "0x1",
      "gasLimit": "0x1c9c380",
      "gasUsed": "0x5208",
      "timestamp": "0x64000001",
      "extraData": "0x",
      "baseFeePerGas": "0x7",
      "blockHash": "0xaaa111...",
      "transactions": ["0xf86c..."],
      "withdrawals": [],
      "blobGasUsed": "0x0",
      "excessBlobGas": "0x0"
    },
    [],
    "0xbeaconroot0000000000000000000000000000000000000000000000000000",
    [],
    [
      "0xf86c0a8502540be400825208..."
    ]
  ],
  "id": 2
}
```

**Response (valid):**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "status": "VALID",
    "latestValidHash": "0xaaa111...",
    "validationError": null
  }
}
```

**Response (unsatisfied, strict mode):**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "status": "INCLUSION_LIST_UNSATISFIED",
    "latestValidHash": null,
    "validationError": "Inclusion list transaction at index 0 not found in payload"
  }
}
```

---

### engine_forkchoiceUpdatedV4

Updates the forkchoice state and optionally initiates payload building with inclusion list support. The `payloadAttributes` parameter now supports the `inclusionListTransactions` field.

**Method**: `engine_forkchoiceUpdatedV4`

#### Parameters

| Index | Name                 | Type     | Description                                    |
|-------|---------------------|----------|------------------------------------------------|
| 0     | `forkchoiceState`    | `Object` | Head, safe, and finalized block hashes         |
| 1     | `payloadAttributes`  | `Object` or `null` | Payload attributes for block building |

#### EnginePayloadAttributesParameter Structure

```json
{
  "timestamp": "QUANTITY",
  "prevRandao": "DATA (32 bytes)",
  "suggestedFeeRecipient": "DATA (20 bytes)",
  "withdrawals": "Array of WithdrawalV1",
  "parentBeaconBlockRoot": "DATA (32 bytes)",
  "slotNumber": "QUANTITY",
  "inclusionListTransactions": "Array of DATA (optional)"
}
```

| Field                          | Type            | Required | Description                                   |
|--------------------------------|-----------------|----------|-----------------------------------------------|
| `timestamp`                    | `QUANTITY`      | Yes      | Payload timestamp                             |
| `prevRandao`                   | `DATA`          | Yes      | Previous RANDAO value                         |
| `suggestedFeeRecipient`        | `DATA`          | Yes      | Suggested fee recipient address               |
| `withdrawals`                  | `Array`         | Yes      | Withdrawal objects                            |
| `parentBeaconBlockRoot`        | `DATA`          | Yes      | Parent beacon block root                      |
| `slotNumber`                   | `QUANTITY`      | Yes      | Slot number                                   |
| `inclusionListTransactions`    | `Array of DATA` | No       | Hex-encoded IL transaction bytes              |

#### Validation

- Returns `-38003: Invalid payload attributes` if the `inclusionListTransactions` field contains invalid entries (null/empty elements, invalid hex, or exceeds `MAX_BYTES_PER_INCLUSION_LIST`).
- When `inclusionListTransactions` is provided, the payload builder prioritizes these transactions during block construction.

#### Errors

| Code   | Message                     | Description                             |
|--------|-----------------------------|-----------------------------------------|
| -38002 | Invalid forkchoice state    | Invalid forkchoice state parameters     |
| -38003 | Invalid payload attributes  | Malformed payload attributes            |

#### Example

**Request:**
```json
{
  "jsonrpc": "2.0",
  "method": "engine_forkchoiceUpdatedV4",
  "params": [
    {
      "headBlockHash": "0xe0a94c...",
      "safeBlockHash": "0xe0a94c...",
      "finalizedBlockHash": "0xe0a94c..."
    },
    {
      "timestamp": "0x64000001",
      "prevRandao": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "suggestedFeeRecipient": "0x4bbeeb066ed09b7aed07bf39eee0460dfa261520",
      "withdrawals": [],
      "parentBeaconBlockRoot": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "slotNumber": "0x1",
      "inclusionListTransactions": [
        "0xf86c0a8502540be400825208..."
      ]
    }
  ],
  "id": 3
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "payloadStatus": {
      "status": "VALID",
      "latestValidHash": "0xe0a94c...",
      "validationError": null
    },
    "payloadId": "0x0000000000000001"
  }
}
```

---

## Configuration Options

### CLI Flags

#### `--engine-inclusion-list-validation-mode`

Sets the inclusion list validation behavior.

| Value    | Description                                                            |
|----------|------------------------------------------------------------------------|
| `STRICT` | (Default) Rejects payloads that do not satisfy IL constraints. Returns `INCLUSION_LIST_UNSATISFIED`. |
| `LENIENT`| Logs warnings for violations but accepts the payload. Returns `VALID`. |

**Example:**
```bash
besu --engine-inclusion-list-validation-mode=LENIENT
```

#### `--engine-inclusion-list-selector-type`

Sets the transaction selection strategy for `engine_getInclusionListV1`.

| Value     | Description                                                      |
|-----------|------------------------------------------------------------------|
| `DEFAULT` | (Default) Priority-based selection by gas price, filtering blobs |

**Example:**
```bash
besu --engine-inclusion-list-selector-type=DEFAULT
```

---

## Metrics

The following metrics are available under the `RPC` category:

| Metric Name                                        | Type    | Description                                        |
|----------------------------------------------------|---------|----------------------------------------------------|
| `engine_inclusion_list_transactions_generated`      | Counter | Number of IL transactions generated                |
| `engine_inclusion_list_bytes_generated`             | Counter | Total bytes of generated IL transactions           |
| `engine_inclusion_list_selector_duration_ms`        | Counter | Time spent selecting IL transactions (milliseconds)|
| `engine_inclusion_list_validation_failures`         | Counter | Number of IL validation failures                   |

---

## Constants

| Name                          | Value | Description                                      |
|-------------------------------|-------|--------------------------------------------------|
| `MAX_BYTES_PER_INCLUSION_LIST`| 8192  | Maximum total bytes for all IL transactions (2^13)|

---

## Error Codes

| Code   | Name                         | Description                              |
|--------|------------------------------|------------------------------------------|
| -38003 | Invalid payload attributes   | Malformed `EnginePayloadAttributesParameter` |
| -38006 | Unknown parent block         | Parent block hash not found              |

## Engine Status Values

| Status                       | Description                                           |
|------------------------------|-------------------------------------------------------|
| `VALID`                      | Payload is valid (IL satisfied or lenient mode)        |
| `INVALID`                    | Payload or IL data is malformed                        |
| `INCLUSION_LIST_UNSATISFIED` | IL constraints not met (strict mode only)              |
| `SYNCING`                    | Node is syncing, cannot validate yet                   |
| `ACCEPTED`                   | Payload accepted but not fully validated               |
