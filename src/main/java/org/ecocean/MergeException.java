package org.ecocean;
import java.util.Set;
import java.util.HashSet;

public class MergeException extends IllegalArgumentException {
    private Set<String> fields;
    public MergeException(String message, Set<String> fields) {
        super(message);
        this.fields = fields;
    }
    public MergeException(String message, String field) {
        super(message);
        this.fields = new HashSet<String>();
        this.fields.add(field);
    }

    public Set<String> getFields() {
        return fields;
    }
}


