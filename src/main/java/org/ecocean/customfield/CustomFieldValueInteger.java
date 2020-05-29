package org.ecocean.customfield;

public class CustomFieldValueInteger extends CustomFieldValue {
    private Integer value = null;
    public CustomFieldValueInteger() {
    }
    public CustomFieldValueInteger(CustomFieldDefinition def) {
        super(def);
    }
    public CustomFieldValueInteger(CustomFieldDefinition def, Object val) {
        super(def);
        this.setValue(val);
    }

    public Object getValue() {
        return this.value;
    }
    public void setValue(Object obj) {
        if ((obj == null) || !(obj instanceof Integer)) {
            this.value = null;
        } else {
            this.value = (Integer)obj;
        }
    }
}

