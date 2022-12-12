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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class SSZUtil {
    public static long decodeContainer(final SSZReader input,final long length, final SSZType... elements) {
        List<OffsetElement<?>> variableElements = new ArrayList<>();
        long read =0;
        for (SSZType element : elements) {
            if (element.isFixedSize()) {
                if (element instanceof SSZFixedType) {
                    element.decodeFrom(input,((SSZFixedType) element).getFixedSize());
                    read += ((SSZFixedType) element).getFixedSize();
                } else {
                    throw new RuntimeException("Unknown fixed size element" + element.getClass().getName());
                }
            } else {
                variableElements.add(new OffsetElement<>(input.readUInt32(), element));
                read+=4;
            }
        }
        for (int i = 0; i < variableElements.size(); i++) {
            OffsetElement<?> element = variableElements.get(i);
            long elementEnd = i== variableElements.size()-1?length:variableElements.get(i+1).offset;
            read+= element.decodeFrom(input, elementEnd-element.offset);
        }
        return read;
    }

    public static class OffsetElement <T extends SSZType> {
        final long offset;
        final T element;

        public OffsetElement(final long offset, final T element) {
            this.offset = offset;
            this.element = element;
        }

        public long decodeFrom(final SSZReader input, final long length) {
            return element.decodeFrom(input, length);
        }

        public T getElement() {
            return element;
        }
    }

    public interface SSZType {
        boolean isFixedSize();
        long decodeFrom(SSZReader input, final long length);
    }

    public interface SSZFixedType extends SSZType {
        @Override
        default boolean isFixedSize() {
            return true;
        }
        int getFixedSize();
    }

    public abstract static class FixedTypeSSZWrapper<T> implements SSZFixedType {
        private T value;
        private final int fixedSize;

        protected FixedTypeSSZWrapper(final int fixedSize) {
            this.fixedSize = fixedSize;
        }

        public T getValue() {
            return value;
        }

        public void setValue(final T value) {
            this.value = value;
        }

        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize(){
            return fixedSize;
        }

    }

    public static class Uint256SSZWrapper extends FixedTypeSSZWrapper<UInt256> {
        public Uint256SSZWrapper() {
            super(32);
            setValue(UInt256.ZERO);
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            setValue(input.readUInt256());
            return 32;
        }
    }

    public static class Uint64SSZWrapper extends FixedTypeSSZWrapper<Long> {
        public Uint64SSZWrapper() {
            super(8);
            setValue(0L);
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            setValue(input.readUInt64());
            return 8;
        }
    }

    public static class SSZUnion implements SSZType {
        private List<Class<? extends SSZType>> types = new ArrayList<>();
        private Class<? extends SSZType> type;
        private SSZType value;

        public SSZUnion(final List<Class<? extends SSZType>> types) {
            this.types =types;
            this.type = this.types.get(0);
            try {
                value = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            final int xxx = input.readInt8();
            type = types.get(xxx);
            try {
                value = type.getDeclaredConstructor().newInstance();
                return value.decodeFrom(input, length-1)+1;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isFixedSize() {
            return false;
        }

        public Class<? extends SSZType> getType() {
            return type;
        }

        public SSZType getValue() {
            return value;
        }
    }

    public static class SSZNone implements SSZFixedType {
        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            if (length != 0) {
                throw new RuntimeException("Invalid length for SSZNone");
            }
            return 0;
        }

        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize() {
            return 0;
        }
    }

    public static class SSZAddress implements SSZType{
        private Bytes address;

        public SSZAddress() {
            address = Bytes.EMPTY;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            address = input.readAddress();
            return 20;
        }

        @Override
        public boolean isFixedSize() {
            return true;
        }

        public Address getAddress() {
            return Address.wrap(address);
        }
    }

    public static class BytesListWrapper implements SSZType {
        private List<Bytes> value;
        private final int length;

        public BytesListWrapper(final int length) {
            this.length = length;
            value = new ArrayList<>();
        }

        public List<Bytes> getValue() {
            return value;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            if (length!= this.length) {
                throw new RuntimeException("Invalid length for BytesListWrapper");
            }
            value = input.readFixedBytesList((int)length);
            return length;
        }

        @Override
        public boolean isFixedSize() {
            return false;
        }
    }

    public static class SSZVariableSizeList<T extends SSZType> implements SSZType {
        private final Supplier<T> supplier;
        private final List<T> list = new ArrayList<>();

        public SSZVariableSizeList(final Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public boolean isFixedSize() {
            return false;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            final long firstOffset = input.readUInt32();
            if (firstOffset == 0) {
                return 4;
            }

            int size = (int) (firstOffset / 4);
            List<OffsetElement<T>> offsetElements = new ArrayList<>(size);
            for (long i = 0, offset = firstOffset; i < size; i++, offset += input.readUInt32()) {
                T element = supplier.get();
                OffsetElement<T> offsetElement = new OffsetElement<>(offset, element);
                offsetElements.add(offsetElement);
            }

            long read = (long)size*4;

            for (int i = 0; i < offsetElements.size(); i++) {
                OffsetElement<T> element = offsetElements.get(i);
                long elementEnd = i == offsetElements.size() - 1 ? length : offsetElements.get(i + 1).offset;
                read+=element.decodeFrom(input, elementEnd - element.offset);
                list.add(element.getElement());
            }
            return read;
        }
    }
        public static class SSZFixedSizeList<T extends SSZFixedType> implements SSZType {
            private final Supplier<T> supplier;
            private final List<T> list  = new ArrayList<>();

            public SSZFixedSizeList(final Supplier<T> supplier) {
                this.supplier = supplier;
            }

            @Override
            public long decodeFrom(final SSZReader input, final long length) {
                final T t = supplier.get();
                int size = (int)length / t.getFixedSize();
                for (int i = 0; i < size; i++) {
                    T element = supplier.get();
                    element.decodeFrom(input, element.getFixedSize());
                    list.add(element);
                }
                return (long) size *t.getFixedSize();
            }

        @Override
        public boolean isFixedSize() {
            return false;
        }

        public List<T> getList() {
            return list;
        }
    }
    public static class BooleanSSZWrapper implements SSZUtil.SSZFixedType {
        private boolean value;

        public boolean getValue() {
            return value;
        }

        public void setValue(final boolean value) {
            this.value = value;
        }

        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize() {
            return 1;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            value = input.readBoolean();
            return 1;
        }
    }

    public static class BytesWrapper implements SSZType {
        private Bytes value;
        private final int length;

        public BytesWrapper(final int length) {
            this.length = length;
            value = Bytes.EMPTY;
        }

        public Bytes getValue() {
            return value;
        }

        public void setValue(final Bytes value) {
            this.value = value;
        }

        @Override
        public boolean isFixedSize() {
            return false;
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            if (this.length!=length){
                throw new RuntimeException("length not match");
            }
            value = input.readFixedBytes(this.length);
            return length;
        }
    }

    public static class FixedSizeSSZVector<T extends SSZFixedType> implements SSZFixedType {
        private final Supplier<T> supplier;
        private final int size;
        private final List<T> elements ;

        public FixedSizeSSZVector(final Supplier<T> supplier, final int size) {
            this.supplier = supplier;
            this.size = size;
            elements = new ArrayList<>(size);
        }

        @Override
        public boolean isFixedSize() {
            return true;
        }

        @Override
        public int getFixedSize() {
            return size*supplier.get().getFixedSize();
        }

        @Override
        public long decodeFrom(final SSZReader input, final long length) {
            int read =0;
            for (int i = 0; i < size; i++) {
                final T t = supplier.get();
                t.decodeFrom(input, t.getFixedSize());
                read+=t.getFixedSize();
                elements.add(t);
            }
            return read;
        }
    }
}
