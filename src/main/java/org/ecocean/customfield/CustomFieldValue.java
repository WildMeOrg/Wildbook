package org.ecocean.customfield;

import org.apache.commons.lang3.builder.ToStringBuilder;

public abstract class CustomFieldValue implements java.io.Serializable {
    private int id;
    private CustomFieldDefinition definition = null;

    public CustomFieldValue() {
    }
    public CustomFieldValue(CustomFieldDefinition def) {
        if (def == null) throw new RuntimeException("cannot create CustomFieldValue with null definition");
        this.definition = def;
    }
    public CustomFieldValue(CustomFieldDefinition def, Object val) {
        if (def == null) throw new RuntimeException("cannot create CustomFieldValue with null definition");
        this.definition = def;
        this.setValue(val);
    }

    public CustomFieldDefinition getDefinition() {
        return definition;
    }
    public abstract Object getValue();
    public abstract void setValue(Object obj);
/*
    public Object getValue() {
        return null;
    }
    public void setValue(Object obj) {
        return;
    }
*/

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("value", this.getValue())
                .append("definition", definition)
                .toString();
    }
}

