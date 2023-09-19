package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import java.io.IOException;

public class NewPayloadV1Serializer extends StdSerializer<NewPayloadParameterV1> {

    public NewPayloadV1Serializer() {
        super(NewPayloadParameterV1.class);
    }

    protected void serializeToOpenObject(final NewPayloadParameterV1 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("blockHash", value.getBlockHash().toHexString());
        gen.writeStringField("parentHash", value.getParentHash().toHexString());
        gen.writeStringField("feeRecipient", value.getFeeRecipient().toHexString());
        gen.writeStringField("stateRoot", value.getStateRoot().toHexString());
        gen.writeStringField("blockNumber", Quantity.createUnsigned(value.getBlockNumber()));
        gen.writeStringField("baseFeePerGas", value.getBaseFeePerGas().toString());
        gen.writeStringField("gasLimit", Quantity.createUnsigned(value.getGasLimit()));
        gen.writeStringField("gasUsed", Quantity.createUnsigned(value.getGasUsed()));
        gen.writeStringField("timestamp", Quantity.createUnsigned(value.getTimestamp()));
        gen.writeStringField("extraData", value.getExtraData().toString());
        gen.writeStringField("receiptsRoot", value.getReceiptsRoot().toHexString());
        gen.writeStringField("logsBloom", value.getLogsBloom().toString());
        gen.writeStringField("prevRandao", value.getPrevRandao().toString());
        gen.writeArrayFieldStart("transactions");
        for (String tx : value.getTransactions()) {
            gen.writeString(tx);
        }
        gen.writeEndArray();

    }

    @Override
    public void serialize(final NewPayloadParameterV1 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        serializeToOpenObject(value, gen, provider);
        gen.writeEndObject();
    }
}
