package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import java.io.IOException;
import java.util.List;

public class NewPayloadV3Deserializer extends StdDeserializer<NewPayloadParameterV2> {
    NewPayloadV2Deserializer v2Deserializer = new NewPayloadV2Deserializer();
    ObjectMapper mapper = new ObjectMapper();
    public NewPayloadV3Deserializer() {
        super(NewPayloadParameterV3.class);
    }
    @Override
    public NewPayloadParameterV3 deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode root = p.getCodec().readTree(p);
        Long blobGasUsed = Long.parseUnsignedLong(Quantity.trim(root.get("blobGasUsed").asText()), 16);
        String excessBlobGas = root.get("excessBlobGas").asText();
        NewPayloadParameterV2 v2Param = v2Deserializer.deserialize(root.traverse(p.getCodec()), ctxt);
        NewPayloadParameterV3 v3 = new NewPayloadParameterV3(v2Param, blobGasUsed, excessBlobGas);
        return v3;
    }
}
