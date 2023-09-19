package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import java.io.IOException;

public class NewPayloadV3Serializer extends StdSerializer<NewPayloadParameterV3> {
    private final NewPayloadV2Serializer v2Serializer = new NewPayloadV2Serializer();

    public NewPayloadV3Serializer() {
        super(NewPayloadParameterV3.class);
    }

    protected void serializeToOpenObject(final NewPayloadParameterV3 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        v2Serializer.serializeToOpenObject(value, gen, provider);
        gen.writeStringField("blobGasUsed", Quantity.createUnsigned(value.getBlobGasUsed()));
        gen.writeStringField("excessBlobGas", value.getExcessBlobGas());
    }


    @Override
    public void serialize(final NewPayloadParameterV3 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        serializeToOpenObject(value, gen, provider);
        gen.writeEndObject();
    }
}
