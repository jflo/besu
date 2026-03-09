/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StrictInclusionListValidatorTest {

  private StrictInclusionListValidator validator;

  private static final Bytes TX_A = Bytes.fromHexString("0xaa");
  private static final Bytes TX_B = Bytes.fromHexString("0xbb");
  private static final Bytes TX_C = Bytes.fromHexString("0xcc");
  private static final Bytes TX_D = Bytes.fromHexString("0xdd");

  @BeforeEach
  public void setUp() {
    validator = new StrictInclusionListValidator();
  }

  @Test
  public void validWhenEmptyInclusionList() {
    final InclusionListValidationResult result =
        validator.validate(List.of(TX_A, TX_B), Collections.emptyList());
    assertThat(result.isValid()).isTrue();
    assertThat(result.getStatus()).isEqualTo(InclusionListValidationStatus.VALID);
  }

  @Test
  public void validWhenNullInclusionList() {
    final InclusionListValidationResult result = validator.validate(List.of(TX_A, TX_B), null);
    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void validWhenAllILTransactionsPresent() {
    final InclusionListValidationResult result =
        validator.validate(List.of(TX_A, TX_B, TX_C), List.of(TX_A, TX_B, TX_C));
    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void validWhenILTransactionsInterleavedInPayload() {
    // Payload has extra txs interleaved: D, A, D, B, D, C
    final InclusionListValidationResult result =
        validator.validate(List.of(TX_D, TX_A, TX_D, TX_B, TX_D, TX_C), List.of(TX_A, TX_B, TX_C));
    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void unsatisfiedWhenILTransactionMissing() {
    // IL requires A, B, C but payload only has A, C (missing B)
    final InclusionListValidationResult result =
        validator.validate(List.of(TX_A, TX_C), List.of(TX_A, TX_B, TX_C));
    assertThat(result.getStatus()).isEqualTo(InclusionListValidationStatus.UNSATISFIED);
    assertThat(result.getErrorMessage()).isPresent();
    assertThat(result.getErrorMessage().get()).contains("index 1");
  }

  @Test
  public void validWhenILTransactionsInDifferentOrder() {
    // IL requires A, B but payload has B, A — per EIP-7805 "anywhere-in-block", order doesn't matter
    final InclusionListValidationResult result =
        validator.validate(List.of(TX_B, TX_A), List.of(TX_A, TX_B));
    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void unsatisfiedWhenPayloadEmpty() {
    final InclusionListValidationResult result =
        validator.validate(Collections.emptyList(), List.of(TX_A));
    assertThat(result.getStatus()).isEqualTo(InclusionListValidationStatus.UNSATISFIED);
  }

  @Test
  public void invalidWhenILExceedsMaxBytes() {
    // Create an IL transaction that exceeds MAX_BYTES_PER_INCLUSION_LIST
    final Bytes largeTx =
        Bytes.wrap(new byte[InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST + 1]);
    final InclusionListValidationResult result =
        validator.validate(List.of(largeTx), List.of(largeTx));
    assertThat(result.getStatus()).isEqualTo(InclusionListValidationStatus.INVALID);
    assertThat(result.getErrorMessage()).isPresent();
    assertThat(result.getErrorMessage().get()).contains("MAX_BYTES_PER_INCLUSION_LIST");
  }

  @Test
  public void validWhenILExactlyAtMaxBytes() {
    // Create IL transactions that sum to exactly MAX_BYTES_PER_INCLUSION_LIST
    final Bytes exactTx = Bytes.wrap(new byte[InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST]);
    final InclusionListValidationResult result =
        validator.validate(List.of(exactTx), List.of(exactTx));
    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void invalidWhenMultipleILTransactionsExceedMaxBytes() {
    // Two transactions that individually fit but together exceed the limit
    final int halfPlus = (InclusionListConstants.MAX_BYTES_PER_INCLUSION_LIST / 2) + 1;
    final Bytes tx1 = Bytes.wrap(new byte[halfPlus]);
    final Bytes tx2 = Bytes.wrap(new byte[halfPlus]);
    final InclusionListValidationResult result =
        validator.validate(List.of(tx1, tx2), List.of(tx1, tx2));
    assertThat(result.getStatus()).isEqualTo(InclusionListValidationStatus.INVALID);
  }
}
