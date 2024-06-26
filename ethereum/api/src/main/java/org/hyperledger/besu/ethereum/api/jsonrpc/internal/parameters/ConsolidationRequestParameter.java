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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConsolidationRequestParameter {

  private final String sourceAddress;
  private final String sourcePubKey;
  private final String targetPubKey;

  @JsonCreator
  public ConsolidationRequestParameter(
      @JsonProperty("sourceAddress") final String sourceAddress,
      @JsonProperty("sourcePubkey") final String sourcePubKey,
      @JsonProperty("targetPubkey") final String targetPubKey) {
    this.sourceAddress = sourceAddress;
    this.sourcePubKey = sourcePubKey;
    this.targetPubKey = targetPubKey;
  }

  public static ConsolidationRequestParameter fromConsolidationRequest(
      final ConsolidationRequest consolidationRequest) {
    return new ConsolidationRequestParameter(
        consolidationRequest.getSourceAddress().toHexString(),
        consolidationRequest.getSourcePublicKey().toHexString(),
        consolidationRequest.getTargetPublicKey().toHexString());
  }

  public ConsolidationRequest toConsolidationRequest() {
    return new ConsolidationRequest(
        Address.fromHexString(sourceAddress),
        BLSPublicKey.fromHexString(sourcePubKey),
        BLSPublicKey.fromHexString(targetPubKey));
  }

  @JsonGetter
  public String getSourceAddress() {
    return sourceAddress;
  }

  @JsonGetter
  public String getSourcePubKey() {
    return sourcePubKey;
  }

  @JsonGetter
  public String getTargetPubKey() {
    return targetPubKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ConsolidationRequestParameter that = (ConsolidationRequestParameter) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(sourcePubKey, that.sourcePubKey)
        && Objects.equals(targetPubKey, that.targetPubKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, sourcePubKey, targetPubKey);
  }

  @Override
  public String toString() {
    return "ConsolidationRequestParameter{"
        + "sourceAddress='"
        + sourceAddress
        + '\''
        + ", sourcePubKey='"
        + sourcePubKey
        + '\''
        + ", targetPubKey='"
        + targetPubKey
        + '\''
        + '}';
  }
}
