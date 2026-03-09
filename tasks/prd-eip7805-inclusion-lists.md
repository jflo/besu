# PRD: EIP-7805 Inclusion Lists Implementation

## Introduction

Implement EIP-7805 (Inclusion Lists) support in Hyperledger Besu to enable the next Ethereum fork. Inclusion lists allow proposers to specify transactions that must be included in blocks, providing censorship resistance and transaction inclusion guarantees. This implementation adds three new/updated Engine API methods, new data structures, validation logic, and pluggable transaction selection strategies.

## Goals

- Achieve full EIP-7805 specification compliance for production Ethereum network
- Implement all three Engine API methods: `engine_getInclusionListV1`, `engine_newPayloadV5`, `engine_forkchoiceUpdatedV4`
- Update `EnginePayloadAttributesParameter` structure with `inclusionListTransactions` field
- Implement block validation logic with configurable strict/lenient modes
- Create pluggable architecture for identifying transactions in the transaction pool that have been censored
- Use censorship detection to compose an inclusion list
- Ensure backward compatibility with existing Engine API versions
- Provide comprehensive unit and integration tests

## User Stories

### US-001: Add EnginePayloadParameter data structure
**Description:** As a developer, I need the new `EnginePayloadAttributesParameter` structure to support inclusion list transactions in payload building.

**Acceptance Criteria:**
- [ ] Add `inclusionListTransactions` field (List of transaction byte arrays)
- [ ] Implement JSON serialization/deserialization
- [ ] Add validation for inclusionListTransactions format
- [ ] Typecheck/lint passes
- [ ] Unit tests verify structure creation and serialization

### US-002: Define InclusionListValidator interface
**Description:** As a developer, I need a new step in my block validation where I apply an InclusionListValidator

**Acceptance Criteria:**
- [ ] Create `InclusionListValidator` interface with `validate(ExecutionPayload, List<Transaction>)` method
- [ ] Method returns validation result with status: VALID, INVALID, or UNSATISFIED
- [ ] Include detailed error messages in validation result
- [ ] Typecheck/lint passes
- [ ] Unit tests verify interface contract

### US-003: Implement strict inclusion list validator
**Description:** As a node operator, I need strict validation that enforces EIP-7805 inclusion list constraints to ensure spec compliance.

**Acceptance Criteria:**
- [ ] Implement `StrictInclusionListValidator` per EIP-7805 specification
- [ ] Validate all IL transactions are included in payload in order (allowing interleaved txs)
- [ ] Return INCLUSION_LIST_UNSATISFIED if constraints violated
- [ ] Respect MAX_BYTES_PER_INCLUSION_LIST constant (8192 bytes)
- [ ] Typecheck/lint passes
- [ ] Unit tests cover: valid cases, missing tx, wrong order, invalid encoding

### US-004: Implement lenient inclusion list validator
**Description:** As a developer, I need a lenient validator for testing environments that logs warnings but doesn't reject non-compliant payloads.

**Acceptance Criteria:**
- [ ] Implement `LenientInclusionListValidator` that logs violations without rejecting
- [ ] Log detailed warning messages for each constraint violation
- [ ] Return VALID status even when inclusion list unsatisfied
- [ ] Include metrics counter for violations encountered
- [ ] Typecheck/lint passes
- [ ] Unit tests verify warnings logged and payload accepted

### US-005: Add configuration for inclusion list validation mode
**Description:** As a node operator, I want to configure strict vs lenient inclusion list validation based on my environment (mainnet vs testnet).

**Acceptance Criteria:**
- [ ] Add `--engine-inclusion-list-validation-mode=strict|lenient` CLI flag
- [ ] Default to strict mode for production safety
- [ ] Load appropriate validator implementation based on config
- [ ] Document configuration option in Besu docs
- [ ] Typecheck/lint passes
- [ ] Integration test verifies flag changes validator behavior

### US-006: Define TransactionSelector interface for IL generation
**Description:** As a developer, I need a pluggable interface for transaction selection so custom strategies can select transactions for inclusion lists.

**Acceptance Criteria:**
- [ ] Create `InclusionListTransactionSelector` interface
- [ ] Method signature: `selectTransactions(blockHash, mempoolTransactions, maxBytes) -> List<Transaction>`
- [ ] Include method to check if selector is enabled
- [ ] Support dependency injection for different selector implementations
- [ ] Typecheck/lint passes
- [ ] Unit tests verify interface contract

### US-007: Implement default transaction selector
**Description:** As a node operator, I need a default transaction selection strategy that prioritizes high-value, stale transactions for inclusion lists.

**Acceptance Criteria:**
- [ ] Implement `DefaultInclusionListSelector` using priority queue
- [ ] Select transactions by effective gas price (highest first, greater than current base fee) and time spent in transaction pool (oldest first)
- [ ] Respect MAX_BYTES_PER_INCLUSION_LIST byte limit (8192 bytes)
- [ ] Filter out blob transactions per EIP-7805 spec
- [ ] Validate transaction nonces are sequential from account state
- [ ] Stop when byte limit reached
- [ ] Typecheck/lint passes
- [ ] Unit tests cover: normal selection, byte limit, blob filtering, nonce validation

### US-008: Implement engine_getInclusionListV1 method
**Description:** As a consensus client, I need to call `engine_getInclusionListV1` to retrieve transactions for the inclusion list based on the execution client's mempool view.

**Acceptance Criteria:**
- [ ] Create `EngineGetInclusionListV1` JSON-RPC method handler
- [ ] Accept `parentHash` parameter (32 bytes)
- [ ] Return array of transaction byte arrays
- [ ] Return `-38006: Unknown parent` error if parentHash block doesn't exist
- [ ] Call configured `InclusionListTransactionSelector` implementation
- [ ] Method timeout set to 1 second per spec
- [ ] Typecheck/lint passes
- [ ] Unit tests verify: success case, unknown parent error, timeout handling
- [ ] Integration test with mock consensus client

### US-009: Update engine_newPayloadV5 method
**Description:** As a consensus client, I need to call `engine_newPayloadV5` with inclusion list transactions to validate payloads against inclusion list constraints.

**Acceptance Criteria:**
- [ ] Extend `EngineNewPayload` to support V5 parameters
- [ ] Add `inclusionListTransactions` parameter (5th parameter)
- [ ] Maintain V4 parameters: executionPayload, expectedBlobVersionedHashes, parentBeaconBlockRoot, executionRequests
- [ ] Call configured `InclusionListValidator` to check constraints
- [ ] Return `INCLUSION_LIST_UNSATISFIED` status when validation fails (strict mode)
- [ ] Return `VALID` status in lenient mode even if unsatisfied
- [ ] Remove `INVALID_BLOCK_HASH` status per spec (use `INVALID`)
- [ ] Typecheck/lint passes
- [ ] Unit tests verify: valid payload, unsatisfied IL, strict vs lenient modes
- [ ] Integration test with mock consensus client requests

### US-010: Update engine_forkchoiceUpdatedV4 method
**Description:** As a consensus client, I need to call `engine_forkchoiceUpdatedV4` with EnginePayloadAttributesParameter to initiate payload building with inclusion list support.

**Acceptance Criteria:**
- [ ] Update `EngineForkchoiceUpdated` to support updated paramters in EnginePayloadAttributesParameter
- [ ] Accept `EnginePayloadAttributesParameter` (or null) as second parameter
- [ ] Validate `EnginePayloadAttributesParameter` structure, return `-38003: Invalid payload attributes` on failure
- [ ] Pass inclusionListTransactions to payload building process
- [ ] Maintain V3 functionality for forkchoice state updates
- [ ] Method timeout remains 8 seconds per spec
- [ ] Typecheck/lint passes
- [ ] Unit tests verify: V4 attributes validation, null attributes handling, invalid attributes error
- [ ] Integration test with payload building

### US-011: Update payload building to respect inclusion lists
**Description:** As a block proposer, I need payload building to include inclusion list transactions in constructed blocks according to EIP-7805 constraints.

**Acceptance Criteria:**
- [ ] Modify `BlockTransactionSelector` to prioritize IL transactions
- [ ] Include all IL transactions in order (allowing interleaved non-IL txs)
- [ ] Validate IL transaction execution doesn't fail
- [ ] Track IL transaction inclusion in built payload
- [ ] Log metrics for IL transactions included vs requested
- [ ] Handle case where IL transaction becomes invalid during block building
- [ ] Typecheck/lint passes
- [ ] Unit tests verify: IL txs included, ordering preserved, failed tx handling
- [ ] Integration test builds valid payload with IL

### US-012: Add MAX_BYTES_PER_INCLUSION_LIST constant
**Description:** As a developer, I need the MAX_BYTES_PER_INCLUSION_LIST constant defined to enforce byte limits on inclusion lists.

**Acceptance Criteria:**
- [ ] Define constant `MAX_BYTES_PER_INCLUSION_LIST = 8192` (2^13)
- [ ] Use constant in validation and transaction selection
- [ ] Add constant to appropriate configuration class
- [ ] Document constant value and purpose
- [ ] Typecheck/lint passes
- [ ] Unit tests verify constant used in byte limit checks

### US-013: Add EngineApiConfiguration for IL settings
**Description:** As a node operator, I need consolidated configuration for inclusion list behavior including validation mode and selector strategy.

**Acceptance Criteria:**
- [ ] Add `InclusionListConfiguration` class with validation mode and selector type
- [ ] Support CLI flags: `--engine-inclusion-list-validation-mode`, `--engine-inclusion-list-selector-type`
- [ ] Default values: strict validation, default selector
- [ ] Wire configuration to validator and selector instantiation
- [ ] Typecheck/lint passes
- [ ] Unit tests verify configuration loading and defaults

### US-014: Integration test for complete IL workflow
**Description:** As a QA engineer, I need end-to-end integration tests that verify the complete inclusion list workflow from generation to validation.

**Acceptance Criteria:**
- [ ] Test full flow: getInclusionListV1 → forkchoiceUpdatedV4 → payload building → newPayloadV5
- [ ] Mock consensus client making sequential Engine API calls
- [ ] Verify IL transactions generated from mempool
- [ ] Verify payload built includes IL transactions
- [ ] Verify newPayloadV5 validates IL constraints
- [ ] Test both strict and lenient validation modes
- [ ] Typecheck/lint passes
- [ ] Integration test suite passes

### US-015: Add logging and metrics for IL operations
**Description:** As a node operator, I need comprehensive logging and metrics to monitor inclusion list behavior in production.

**Acceptance Criteria:**
- [ ] Log IL generation: transaction count, total bytes, selection strategy
- [ ] Log IL validation: result, violations detected, validation mode
- [ ] Add metrics: `engine_inclusion_list_transactions_generated`, `engine_inclusion_list_validation_failures`
- [ ] Add metrics: `engine_inclusion_list_bytes_generated`, `engine_inclusion_list_selector_duration_ms`
- [ ] Log at appropriate levels: DEBUG for details, INFO for key events, WARN for violations
- [ ] Typecheck/lint passes
- [ ] Unit tests verify metrics incremented correctly

### US-016: Update API documentation
**Description:** As an integrator, I need updated API documentation that describes the new Engine API methods and parameters for EIP-7805.

**Acceptance Criteria:**
- [ ] Document `engine_getInclusionListV1` method: params, response, errors, examples
- [ ] Document `engine_newPayloadV5` method: new parameter, new status values
- [ ] Document `engine_forkchoiceUpdatedV4` method: PayloadAttributesV4 structure
- [ ] Add EIP-7805 overview section explaining inclusion list concept
- [ ] Include configuration options documentation
- [ ] Add example JSON-RPC requests and responses
- [ ] Typecheck/lint passes

## Functional Requirements

### Engine API Methods

- FR-1: Implement `engine_getInclusionListV1(parentHash) -> inclusionListTransactions[]`
  - FR-1.1: Return transaction byte arrays selected by configured selector
  - FR-1.2: Respect MAX_BYTES_PER_INCLUSION_LIST byte limit (8192 bytes)
  - FR-1.3: Exclude blob transactions from inclusion list
  - FR-1.4: Return error `-38006: Unknown parent` if parentHash not found
  - FR-1.5: Complete within 1 second timeout

- FR-2: Implement `engine_newPayloadV5(executionPayload, expectedBlobVersionedHashes, parentBeaconBlockRoot, executionRequests, inclusionListTransactions) -> PayloadStatusV1`
  - FR-2.1: Accept inclusionListTransactions as 5th parameter
  - FR-2.2: Validate payload satisfies inclusion list constraints
  - FR-2.3: Return INCLUSION_LIST_UNSATISFIED status when validation fails (strict mode)
  - FR-2.4: Return VALID status in lenient mode with warning logs
  - FR-2.5: Maintain backward compatibility with V4 validation logic

- FR-3: Implement `engine_forkchoiceUpdatedV4(forkchoiceState, payloadAttributes) -> ForkchoiceUpdatedResponse`
  - FR-3.1: Accept `EnginePayloadAttributesParameter` with inclusionListTransactions field
  - FR-3.2: Validate `EnginePayloadAttributesParameter` structure completeness
  - FR-3.3: Return error `-38003: Invalid payload attributes` on validation failure
  - FR-3.4: Pass inclusionListTransactions to payload building routine
  - FR-3.5: Complete within 8 second timeout

### Data Structures

- FR-4: Define PayloadAttributesV4 structure in EnginePayloadAttributesParameter
  - FR-4.1: Include all V3 fields: timestamp, prevRandao, suggestedFeeRecipient, withdrawals, parentBeaconBlockRoot
  - FR-4.2: Add inclusionListTransactions field (array of transaction byte lists) as optional
  - FR-4.3: Support JSON serialization matching Engine API spec format
  - FR-4.4: Validate transaction encoding (TransactionType || TransactionPayload or LegacyTransaction per EIP-2718)

### Validation Logic

- FR-5: Strict inclusion list validation
  - FR-5.1: All IL transactions must appear in payload in specified order
  - FR-5.2: Non-IL transactions may be interleaved between IL transactions
  - FR-5.3: IL transactions must be valid (proper encoding, valid signatures, sequential nonces)
  - FR-5.4: Total IL size must not exceed MAX_BYTES_PER_INCLUSION_LIST
  - FR-5.5: Return INCLUSION_LIST_UNSATISFIED if any constraint violated

- FR-6: Lenient inclusion list validation
  - FR-6.1: Log warnings for constraint violations without rejecting payload
  - FR-6.2: Return VALID status even when IL unsatisfied
  - FR-6.3: Track metrics for violations in lenient mode

### Transaction Selection

- FR-7: Pluggable transaction selector architecture
  - FR-7.1: Define InclusionListTransactionSelector interface
  - FR-7.2: Support dependency injection of selector implementations
  - FR-7.3: Allow configuration to choose selector type

- FR-8: Default transaction selector implementation
  - FR-8.1: Select transactions from mempool by effective gas price (descending)
  - FR-8.2: Filter out blob transactions
  - FR-8.3: Validate transaction nonces are sequential from account state
  - FR-8.4: Stop when MAX_BYTES_PER_INCLUSION_LIST limit reached
  - FR-8.5: Return empty list if mempool empty or no valid transactions

### Payload Building

- FR-9: Payload building must respect inclusion lists
  - FR-9.1: Include all IL transactions in constructed payload
  - FR-9.2: Maintain IL transaction ordering (allowing interleaved non-IL txs)
  - FR-9.3: Validate IL transaction execution succeeds during block building
  - FR-9.4: Handle IL transactions that become invalid during building

### Configuration

- FR-10: Configuration options for IL behavior
  - FR-10.1: CLI flag `--engine-inclusion-list-validation-mode=strict|lenient` (default: strict)
  - FR-10.2: CLI flag `--engine-inclusion-list-selector-type=default|custom` (default: default)
  - FR-10.3: Configuration validates flag values at startup
  - FR-10.4: Configuration documented in Besu CLI reference

### Monitoring

- FR-11: Logging for IL operations
  - FR-11.1: Log IL generation events with transaction count and byte size
  - FR-11.2: Log IL validation results including violations detected
  - FR-11.3: Log at DEBUG level for details, INFO for key events, WARN for violations

- FR-12: Metrics for IL operations
  - FR-12.1: Counter: `engine_inclusion_list_transactions_generated`
  - FR-12.2: Counter: `engine_inclusion_list_validation_failures`
  - FR-12.3: Gauge: `engine_inclusion_list_bytes_generated`
  - FR-12.4: Histogram: `engine_inclusion_list_selector_duration_ms`

### Backward Compatibility

- Existing Engine API versions (V1-V4) continue to function unchanged
- New V5 methods only activated for forks with EIP-7805 enabled
- Configuration defaults (strict mode) provide safe behavior for production

### Error Handling

- Specific error codes per spec: `-38006` for unknown parent, `-38003` for invalid attributes
- New `INCLUSION_LIST_UNSATISFIED` status distinct from `INVALID` for clear debugging
- Detailed validation error messages in lenient mode logs

### Integration Points

- **Engine API module:** Add new methods to existing Engine API handler
- **Payload building:** Modify BlockTransactionSelector to prioritize IL transactions
- **Mempool:** Read transactions from pending transaction pool for IL generation
- **Configuration:** Extend EngineConfiguration with IL-specific settings
- **Metrics:** Integrate with existing Besu metrics system (Prometheus)

### Testing Strategy

- **Unit Tests:** Each validator, selector, and method handler tested independently with mocked dependencies
- **Integration Tests:** Full Engine API call sequences tested with mock consensus client
- **Test Scenarios:**
  - Valid IL generation and validation
  - IL constraint violations (missing tx, wrong order, invalid encoding)
  - Blob transaction filtering
  - Byte limit enforcement
  - Unknown parent error
  - Strict vs lenient mode differences
  - Payload building with IL transactions

## Success Metrics

- All three Engine API methods implemented and passing integration tests
- 100% EIP-7805 specification compliance verified through test coverage
- Successful interop testing with at least one consensus client (e.g., Teku, Lighthouse)
- Configuration options documented and validated
- Zero regressions in existing Engine API V1-V4 functionality
- Metrics and logging provide clear operational visibility

## Open Questions

3. **IL transaction invalidation:** If an IL transaction becomes invalid during payload building (e.g., account state changed), should we retry without that transaction or fail the entire payload build?

5. **Fork activation:** How should fork activation be detected? Via existing fork configuration mechanism or new EIP-7805-specific flag?

6. **Custom selectors:** Should we provide examples of custom selector implementations (e.g., for testing), or just the interface and default?

## Implementation Phases

### Phase 1: Core Structures and Validation (US-001 to US-006)
- Data structures, interfaces, validators, configuration
- **Exit Criteria:** PayloadAttributesV4 defined, validators implemented and tested

### Phase 2: Engine API Methods (US-007 to US-010)
- Implement all three Engine API methods
- **Exit Criteria:** All methods callable via JSON-RPC, unit tests passing

### Phase 3: Payload Building Integration (US-011 to US-013)
- Integrate IL support into payload building process
- **Exit Criteria:** Payloads built with IL transactions, configuration wired

### Phase 4: Testing and Observability (US-014 to US-016)
- Integration tests, metrics, logging, documentation
- **Exit Criteria:** Full test suite passing, documentation complete

## Appendix: EIP-7805 Key Concepts

**Inclusion List:** A list of transactions that must be included in a block. Provides censorship resistance by requiring proposers to include specific transactions.

**MAX_BYTES_PER_INCLUSION_LIST:** Maximum total size of transactions in an inclusion list (8192 bytes = 2^13). Limits computational burden.

**INCLUSION_LIST_UNSATISFIED:** New payload status indicating the payload doesn't satisfy inclusion list constraints (transactions missing or wrong order).

**Blob Transactions:** Type 3 transactions (EIP-4844) carrying blob data. Must NOT be included in inclusion lists.

**Transaction Ordering:** IL transactions must appear in the payload in the same order as specified in the inclusion list. Non-IL transactions may be interleaved.
