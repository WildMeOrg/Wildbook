package org.ecocean.api;
import java.util.Set;
import java.util.HashSet;

public class ApiValueException extends IllegalArgumentException {
    private Set<String> fields;
    public ApiValueException(String message, Set<String> fields) {
        super(message);
        this.fields = fields;
    }
    public ApiValueException(String message, String field) {
        super(message);
        this.fields = new HashSet<String>();
        this.fields.add(field);
    }

    public Set<String> getFields() {
        return fields;
    }
}


