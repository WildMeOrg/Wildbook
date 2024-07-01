package org.ecocean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class BaseSerializer extends StdSerializer<Base> {
    public BaseSerializer() {
        this(null);
    }

    public BaseSerializer(Class<Base> t) {
        super(t);
    }

    @Override public void serialize(Base obj, JsonGenerator jgen, SerializerProvider provider)
    throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        obj.opensearchDocumentSerializer(jgen);
        jgen.writeEndObject();
    }
}
