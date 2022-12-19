/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and lengthations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.encoding.ssz;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class SignedBlobTransaction implements SSZUtil.SSZType {
    public static final int MAX_CALL_DATA_SIZE = 16777216; //2**24

    BlobTransaction message = new BlobTransaction();
    ECDSASignature signature = new ECDSASignature();
    public BlobTransaction getMessage() {
        return message;
    }

    public void setMessage(final BlobTransaction message) {
        this.message = message;
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public void setSignature(final ECDSASignature signature) {
        this.signature = signature;
    }

    @Override
    public boolean isFixedSize() {
        return false;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
        return SSZUtil.decodeContainer(input, length, message,signature);
    }

    public static class BlobTransaction implements SSZUtil.SSZType {
        SSZUtil.Uint256SSZWrapper chainId = new SSZUtil.Uint256SSZWrapper();
        SSZUtil.Uint64SSZWrapper nonce = new SSZUtil.Uint64SSZWrapper();
        SSZUtil.Uint256SSZWrapper maxPriorityFeePerGas = new SSZUtil.Uint256SSZWrapper();
        SSZUtil.Uint256SSZWrapper maxFeePerGas = new SSZUtil.Uint256SSZWrapper();
        SSZUtil.Uint64SSZWrapper gas = new SSZUtil.Uint64SSZWrapper();
        MaybeAddress address = new MaybeAddress();
        SSZUtil.Uint256SSZWrapper value = new SSZUtil.Uint256SSZWrapper();
        SSZUtil.BytesListWrapper data = new SSZUtil.BytesListWrapper(MAX_CALL_DATA_SIZE);

        SSZUtil.SSZVariableSizeList<AccessTuple> accessList = new SSZUtil.SSZVariableSizeList<>(AccessTuple::new);
        SSZUtil.Uint256SSZWrapper maxFeePerData = new SSZUtil.Uint256SSZWrapper();

        SSZUtil.SSZFixedSizeList<VersionedHash> blobVersionedHashes = new SSZUtil.SSZFixedSizeList<>(VersionedHash::new);

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            return SSZUtil.decodeContainer(input, length,chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gas,
                    address, value, data,accessList,maxFeePerData,blobVersionedHashes);
        }

        public UInt256 getChainId() {
            return chainId.getValue();
        }

        public Long getNonce() {
            return nonce.getValue();
        }

        public UInt256 getMaxPriorityFeePerGas() {
            return maxPriorityFeePerGas.getValue();
        }

        public UInt256 getMaxFeePerGas() {
            return maxFeePerGas.getValue();
        }

        public Long getGas() {
            return gas.getValue();
        }

        public Optional<Address> getAddress() {
            return address.getAddress().map(SSZUtil.SSZAddress::getAddress);
        }

        public SSZUtil.Uint256SSZWrapper getValue() {
            return value;
        }

        public SSZUtil.BytesListWrapper getData() {
            return data;
        }

        public SSZUtil.SSZVariableSizeList<AccessTuple> getAccessList() {
            return accessList;
        }

        public SSZUtil.Uint256SSZWrapper getMaxFeePerData() {
            return maxFeePerData;
        }

        public SSZUtil.SSZFixedSizeList<VersionedHash> getBlobVersionedHashes() {
            return blobVersionedHashes;
        }
    }

    public static class MaybeAddress implements SSZUtil.SSZType{

        private SSZUtil.SSZAddress address;

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            return SSZUtil.decodeUnion(input,length,List.of( SSZUtil.SSZNone::new, () -> {
                address = new SSZUtil.SSZAddress();
                return address;
            }));
        }

        public Optional<SSZUtil.SSZAddress> getAddress() {
            return Optional.ofNullable(address);
        }
    }

    public static class AccessTuple implements SSZUtil.SSZType {
        SSZUtil.SSZAddress address = new SSZUtil.SSZAddress();
        SSZUtil.SSZVariableSizeList<SSZUtil.Uint256SSZWrapper> storageKeys = new SSZUtil.SSZVariableSizeList<>(SSZUtil.Uint256SSZWrapper::new);

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            return SSZUtil.decodeContainer(input, length, address, storageKeys);
        }

    }


    public static class ECDSASignature implements SSZUtil.SSZFixedType {
        SSZUtil.BooleanSSZWrapper yParity = new SSZUtil.BooleanSSZWrapper();
        SSZUtil.Uint256SSZWrapper r = new SSZUtil.Uint256SSZWrapper();
        SSZUtil.Uint256SSZWrapper s = new SSZUtil.Uint256SSZWrapper();


        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize() {
            return 1+32+32;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            return SSZUtil.decodeContainer(input, length,yParity, r, s);
        }
    }



    public static class VersionedHash implements SSZUtil.SSZFixedType {

        private Bytes bytes;

        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize() {
            return 32;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            bytes = input.readFixedBytes(32);
            return 32;
        }

        public Bytes getBytes() {
            return bytes;
        }
    }
}
