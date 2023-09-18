package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;

public class NewPayloadV2Deserializer extends StdDeserializer<NewPayloadParameterV2> {
    NewPayloadV1Deserializer v1Deserializer = new NewPayloadV1Deserializer();
    ObjectMapper mapper = new ObjectMapper();
    public NewPayloadV2Deserializer() {
        super(NewPayloadParameterV2.class);
    }
    @Override
    public NewPayloadParameterV2 deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode root = p.readValueAsTree();
        List<WithdrawalParameter> withdrawals = mapper.convertValue(root.path("withdrawals"), new TypeReference<>() {
        });
        NewPayloadParameterV1 v1Param = v1Deserializer.deserialize(root.traverse(p.getCodec()), ctxt);
        NewPayloadParameterV2 v2 = new NewPayloadParameterV2(v1Param, withdrawals);

        return v2;
    }
}
