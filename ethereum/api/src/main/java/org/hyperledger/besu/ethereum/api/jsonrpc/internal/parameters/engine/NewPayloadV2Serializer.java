package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class NewPayloadV2Serializer extends StdSerializer<NewPayloadParameterV2> {
    private final NewPayloadV1Serializer v1Serializer = new NewPayloadV1Serializer();

    public NewPayloadV2Serializer() {
        super(NewPayloadParameterV2.class);
    }

    protected void serializeToOpenObject(final NewPayloadParameterV2 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        v1Serializer.serializeToOpenObject(value, gen, provider);

        gen.writeArrayFieldStart("withdrawals");
        if(value.getWithdrawals() != null) {
            for (WithdrawalParameter withdrawal : value.getWithdrawals()) {
                gen.writeObject(withdrawal);
            }
        }
        gen.writeEndArray();
    }

    @Override
    public void serialize(final NewPayloadParameterV2 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        serializeToOpenObject(value, gen, provider);
        gen.writeEndObject();
    }
}
