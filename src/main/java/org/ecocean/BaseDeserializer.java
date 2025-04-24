package org.ecocean;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class BaseDeserializer extends StdDeserializer<Base> {
    public BaseDeserializer() {
        this(null);
    }

    public BaseDeserializer(Class<Base> t) {
        super(t);
    }

    @Override public Encounter deserialize(JsonParser jp, DeserializationContext ctxt)
    throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        String id = node.get("id").asText();
        // String itemName = node.get("itemName").asText();
        // int userId = (Integer) ((IntNode) node.get("createdBy")).numberValue();
        Encounter obj = new Encounter();

        obj.setId(id);
        return obj;
    }
}
