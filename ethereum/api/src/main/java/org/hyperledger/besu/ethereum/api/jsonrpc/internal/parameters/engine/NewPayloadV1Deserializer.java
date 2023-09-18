package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.engine;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.io.IOException;
import java.util.List;

public class NewPayloadV1Deserializer extends StdDeserializer<NewPayloadParameterV1> {

    public NewPayloadV1Deserializer() {
        super(NewPayloadParameterV1.class);
    }
    @Override
    public NewPayloadParameterV1 deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode root = p.getCodec().readTree(p);
        ObjectMapper mapper = new ObjectMapper();
        List<String> txs = mapper.convertValue(root.get("transactions"), new TypeReference<List<String>>(){});
        return new NewPayloadParameterV1(
                Hash.fromHexString(root.get("blockHash").asText()),
                Hash.fromHexString(root.get("parentHash").asText()),
                Address.fromHexString(root.get("feeRecipient").asText()),
                Hash.fromHexString(root.get("stateRoot").asText()),
                Long.parseUnsignedLong(root.get("blockNumber").asText(), 16),
                root.get("baseFeePerGas").asText(),
                Long.parseUnsignedLong(root.get("gasLimit").asText(), 16),
                Long.parseUnsignedLong(root.get("gasUsed").asText(), 16),
                Long.parseUnsignedLong(root.get("timestamp").asText(), 16),
                root.get("extraData").textValue(),
                Hash.fromHexString(root.get("receiptsRoot").asText()),
                LogsBloomFilter.fromHexString(root.get("logsBloom").asText()),
                root.get("prevRandao").asText(),
                txs);
    }
}
