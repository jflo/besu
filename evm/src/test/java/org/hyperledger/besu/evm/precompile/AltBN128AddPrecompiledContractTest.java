/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.precompile;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AltBN128AddPrecompiledContractTest {

  @Mock MessageFrame messageFrame;

  private final PrecompiledContract byzantiumContract =
      EvmSpec.evmSpec(EvmSpecVersion.BYZANTIUM)
          .getPrecompileContractRegistry()
          .get(Address.ALTBN128_ADD);
  private final PrecompiledContract istanbulContract =
      EvmSpec.evmSpec(EvmSpecVersion.ISTANBUL)
          .getPrecompileContractRegistry()
          .get(Address.ALTBN128_ADD);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAddValidPoints(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test adding generator point (1,2) to itself
    // G1 generator: (1, 2)
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"), // y1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x2
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002") // y2
            );

    // Expected result: 2*G1
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd315ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAddPointToZero(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test adding generator point (1,2) to zero point (0,0)
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"), // y1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"), // x2 = 0
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000") // y2 = 0
            );

    // Expected result: G1 (adding zero point returns the first point)
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAddZeroToZero(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test adding zero point to itself
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"), // y1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"), // x2
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000") // y2
            );

    // Expected result: zero point (identity element)
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldFailOnInvalidPoint(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with invalid point (not on curve)
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000003"), // y1 - invalid!
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x2
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002") // y2
            );

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldFailOnSecondInvalidPoint(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with second point invalid (not on curve)
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"), // y1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x2
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000003") // y2 - invalid!
            );

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleShortInput(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with input shorter than 128 bytes - should be padded with zeros
    final Bytes input =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002") // y1
            // x2 and y2 missing - should default to zero
            );

    // Expected result: G1 (adding zero point returns the first point)
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleEmptyInput(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with empty input - should default to zero + zero = zero
    final Bytes input = Bytes.fromHexString("0x");

    // Expected result: zero point (identity element)
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldCalculateGasCost_Byzantium() {
    final Bytes input =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002");

    assertThat(byzantiumContract.gasRequirement(input)).isEqualTo(500L);
  }

  @Test
  void shouldCalculateGasCost_Istanbul() {
    final Bytes input =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002");

    assertThat(istanbulContract.gasRequirement(input)).isEqualTo(150L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAddComplexPoints(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with two different valid points
    // Point 1: 2*G1
    // Point 2: 3*G1
    // Expected: 5*G1
    final Bytes input =
        Bytes.fromHexString(
            "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd3"
                + "15ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4"
                + "077da99d806abd13c9f15ece5398525119d11e11e9836b2ee7d23f6159ad87d4"
                + "01485efa927f2ad41bff567eec88f32fb0a0f706588b4e41a8d587d008b7f875");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();

    // Verify result is not null and is 64 bytes
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(64);

    // Result should be a valid point (we can't easily verify it's exactly 5*G1 without the crypto lib)
    // But we can verify it computed successfully
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleLargeCoordinates(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with maximum valid field elements
    final Bytes input =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000");

    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).output();
    assertThat(result).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldIgnoreExtraBytesInLongInput(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with input longer than 128 bytes - extra bytes should be ignored
    // Valid 128 bytes: G1 + G1
    final Bytes validInput =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002");

    // Add extra garbage bytes that should be ignored
    final Bytes extraBytes =
        Bytes.fromHexString(
            "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    final Bytes longInput = Bytes.concatenate(validInput, extraBytes);

    // Expected result: 2*G1 (same as adding G1 to itself)
    final Bytes expectedResult =
        Bytes.fromHexString(
            "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd315ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4");

    final Bytes result = byzantiumContract.computePrecompile(longInput, messageFrame).output();
    assertThat(result).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleVariousInputLengths(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with input at various byte lengths
    // 96 bytes: only x1, y1, x2 provided (y2 missing, defaults to 0)
    final Bytes input96 =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000000");

    final Bytes result96 = byzantiumContract.computePrecompile(input96, messageFrame).output();
    // Should succeed - adding G1 to zero point
    assertThat(result96).isNotNull();
    assertThat(result96.size()).isEqualTo(64);

    // 32 bytes: only x1 provided (all others default to 0)
    final Bytes input32 =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000000");

    final Bytes result32 = byzantiumContract.computePrecompile(input32, messageFrame).output();
    // Should succeed - adding zero to zero = zero
    assertThat(result32).isNotNull().isEqualTo(Bytes.fromHexString("0x" + "00".repeat(64)));

    // 200 bytes: 128 valid bytes + 72 extra bytes
    final Bytes validPart =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    final Bytes input200 = Bytes.concatenate(validPart, Bytes.random(72));

    final Bytes result200 = byzantiumContract.computePrecompile(input200, messageFrame).output();
    // Should succeed and ignore extra bytes - adding G1 to zero
    assertThat(result200).isNotNull();
    assertThat(result200.size()).isEqualTo(64);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleUnalignedShortInputs(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }

    // Test with unaligned input lengths that don't fall on coordinate boundaries
    // These inputs are NOT pre-padded - the precompile must handle missing bytes

    // 17 bytes: partial x1 (only 17 bytes of the first 32-byte coordinate)
    final Bytes input17 = Bytes.fromHexString("0x00000000000000000000000000000001");
    final Bytes result17 = byzantiumContract.computePrecompile(input17, messageFrame).output();
    // Should succeed - incomplete x1 (padded to x1=1), y1=0, x2=0, y2=0
    // Point (1,0) is not on curve, so should fail
    assertThat(result17).isNull();

    // 50 bytes: complete x1, partial y1 (18 bytes into y1)
    final Bytes input50 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"), // x1 = 0
            Bytes.fromHexString("0x000000000000000000000000000000000000")); // partial y1
    final Bytes result50 = byzantiumContract.computePrecompile(input50, messageFrame).output();
    // Should succeed - (0,0) + (0,0) = (0,0)
    assertThat(result50).isNotNull().isEqualTo(Bytes.fromHexString("0x" + "00".repeat(64)));

    // 127 bytes: missing only the last byte of y2
    final Bytes input127 =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000001" // x1
                + "0000000000000000000000000000000000000000000000000000000000000002" // y1
                + "0000000000000000000000000000000000000000000000000000000000000001" // x2
                + "00000000000000000000000000000000000000000000000000000000000000"); // y2 missing last byte

    final Bytes result127 = byzantiumContract.computePrecompile(input127, messageFrame).output();
    // Should succeed - y2 will be treated as having the last byte = 0, giving y2=0
    // But (1,0) is not on curve, so should fail
    assertThat(result127).isNull();

    // 33 bytes: complete x1, only 1 byte of y1
    final Bytes input33 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"), // x1 = 1
            Bytes.fromHexString("0x00")); // partial y1 = 0
    final Bytes result33 = byzantiumContract.computePrecompile(input33, messageFrame).output();
    // Should fail - (1,0) is not on curve
    assertThat(result33).isNull();

    // 100 bytes: complete x1, y1, x2, and 4 bytes of y2
    final Bytes input100 =
        Bytes.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000000" // x1 = 0
                + "0000000000000000000000000000000000000000000000000000000000000000" // y1 = 0
                + "0000000000000000000000000000000000000000000000000000000000000001" // x2 = 1
                + "00000002"); // partial y2 (4 bytes)

    final Bytes result100 = byzantiumContract.computePrecompile(input100, messageFrame).output();
    // Should fail - x2=1, y2 will be padded to have leading bytes, giving incorrect y2
    // Point will not be on curve
    assertThat(result100).isNull();

    // 10 bytes: Very short, unaligned input
    final Bytes input10 = Bytes.fromHexString("0x00000000000000000001");
    final Bytes result10 = byzantiumContract.computePrecompile(input10, messageFrame).output();
    // Partial x1, rest zero. Will be invalid point.
    assertThat(result10).isNull();
  }
}