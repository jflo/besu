/*
 * Copyright Hyperledger Besu Contributors
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

package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.logging.log4j.LogManager;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TxCallDataValidatorTest {

  @Mock ProtocolSchedule protocolSchedule;
  @Mock BlockHeader blockHeader;
  @Mock Block block;
  private TxCallDataValidator blockBodyValidator;
  private Level previousLogLevel;

  private static final KeyPair keyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  @Before
  public void setup() {
    when(block.getHeader()).thenReturn(blockHeader);

    blockBodyValidator = new TxCallDataValidator(protocolSchedule);
    previousLogLevel = LogManager.getLogger(TxCallDataValidator.class).getLevel();
    Configurator.setLevel(TxCallDataValidator.class.getName(), Level.ALL);
  }

  @After
  public void tearDown() {
    Configurator.setLevel(TxCallDataValidator.class.getName(), previousLogLevel);
  }

  @Test
  public void validatesEmptyTXBlockUnderLimit() {

    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(eip1559Transaction(Bytes.EMPTY), frontierTransaction(Bytes.EMPTY)),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isTrue();
  }

  @Test
  public void validatesTXHavingDataBlockUnderLimit() {

    Bytes payload = Bytes.fromBase64String("0xdeadbeef");
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(eip1559Transaction(payload), frontierTransaction(payload)),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isTrue();
  }

  @Test
  public void validatesTXHavingDataOverStipendUnderLimit() {

    byte[] payload = new byte[TxCallDataValidator.CALLDATA_PER_TX_STIPEND + 1];
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(
                    eip1559Transaction(Bytes.of(payload)), frontierTransaction(Bytes.of(payload))),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isTrue();
  }

  @Test
  public void invalidatesTXOverLimit() {
    byte[] rollup =
        new byte
            [TxCallDataValidator.BASE_MAX_CALLDATA_PER_BLOCK
                + TxCallDataValidator.CALLDATA_PER_TX_STIPEND
                + 1];
    Arrays.fill(rollup, (byte) 0xff);
    byte[] payload = new byte[TxCallDataValidator.CALLDATA_PER_TX_STIPEND];
    Arrays.fill(payload, (byte) 0xef);
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(
                    eip1559Transaction(Bytes.of(rollup)), frontierTransaction(Bytes.of(payload))),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isFalse();
  }

  @Test
  public void validatesTXAtLimit() {
    byte[] rollup =
        new byte
            [TxCallDataValidator.BASE_MAX_CALLDATA_PER_BLOCK
                + TxCallDataValidator.CALLDATA_PER_TX_STIPEND];
    Arrays.fill(rollup, (byte) 0xff);
    byte[] payload = new byte[TxCallDataValidator.CALLDATA_PER_TX_STIPEND];
    Arrays.fill(payload, (byte) 0xef);
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(
                    eip1559Transaction(Bytes.of(rollup)), frontierTransaction(Bytes.of(payload))),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isTrue();
  }

  @Test
  public void invalidatesOverStipend() {
    byte[] rollup =
        new byte
            [TxCallDataValidator.BASE_MAX_CALLDATA_PER_BLOCK
                + TxCallDataValidator.CALLDATA_PER_TX_STIPEND];
    Arrays.fill(rollup, (byte) 0xff);
    byte[] payload = new byte[TxCallDataValidator.CALLDATA_PER_TX_STIPEND + 1];
    Arrays.fill(payload, (byte) 0xef);
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(
                    eip1559Transaction(Bytes.of(rollup)), frontierTransaction(Bytes.of(payload))),
                Collections.emptyList()));

    assertThat(this.blockBodyValidator.validateTransactionDataLimit(block)).isFalse();
  }

  private Transaction eip1559Transaction(final Bytes payload) {
    return new TransactionTestFixture()
        .maxFeePerGas(Optional.of(Wei.of(10L)))
        .maxPriorityFeePerGas(Optional.of(Wei.of(1L)))
        .type(TransactionType.EIP1559)
        .payload(payload)
        .createTransaction(keyPair);
  }

  private Transaction frontierTransaction(final Bytes payload) {
    return new TransactionTestFixture()
        .gasPrice(Wei.of(10L))
        .payload(payload)
        .createTransaction(keyPair);
  }
}
