package org.ecocean.customfield;

import org.apache.commons.lang3.builder.ToStringBuilder;
import java.io.IOException;
import org.ecocean.SystemLog;

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

    public static CustomFieldValue makeSpecific(CustomFieldDefinition def, Object val) throws IOException {
        SystemLog.debug("CustomFieldValue.makeSpecific() looking for type={} in obj val={} --- defn={}", def.getType(), val, def);
        switch (def.getType()) {
            case "string":
                return new CustomFieldValueString(def, val);
            case "integer":
                return new CustomFieldValueInteger(def, val);
            case "double":
                return new CustomFieldValueDouble(def, val);
            case "date":
                return new CustomFieldValueDate(def, val);
            default:
                SystemLog.warn("CustomFieldValue.makeSpecific() got bad type on " + def.toString());
                return null;
        }
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("value", this.getValue())
                .append("definition", definition)
                .toString();
    }
}

