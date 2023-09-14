package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class NewPayloadV1Serializer extends StdSerializer<EngineExecutionPayloadParameterV1>
{

    public NewPayloadV1Serializer() {
        super(EngineExecutionPayloadParameterV1.class);
    }
    @Override
    public void serialize(final EngineExecutionPayloadParameterV1 value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("blockHash", value.getBlockHash().toHexString());
        gen.writeStringField("parentHash", value.getParentHash().toHexString());
        gen.writeStringField("feeRecipient", value.getFeeRecipient().toHexString());
        gen.writeStringField("stateRoot", value.getStateRoot().toHexString());
        gen.writeStringField("blockNumber", Long.toUnsignedString(value.getBlockNumber(), 16));
        gen.writeStringField("baseFeePerGas", value.getBaseFeePerGas().toString());
        gen.writeStringField("gasLimit", Long.toUnsignedString(value.getGasLimit(), 16));
        gen.writeStringField("gasUsed", Long.toUnsignedString(value.getGasUsed(), 16));
        gen.writeStringField("timestamp", Long.toUnsignedString(value.getTimestamp(), 16));
        gen.writeStringField("extraData", value.getExtraData().toString());
        gen.writeStringField("receiptsRoot", value.getReceiptsRoot().toHexString());
        gen.writeStringField("logsBloom", value.getLogsBloom().toString());
        gen.writeStringField("prevRandao", value.getPrevRandao().toString());
        gen.writeArrayFieldStart("transactions");
        for (String tx : value.getTransactions()) {
            gen.writeString(tx);
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
