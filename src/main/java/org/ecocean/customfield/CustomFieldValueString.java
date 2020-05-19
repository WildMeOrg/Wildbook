package org.ecocean.customfield;

public class CustomFieldValueString extends CustomFieldValue {
    private String value = null;
    public CustomFieldValueString() {
    }
    public CustomFieldValueString(CustomFieldDefinition def) {
        super(def);
    }
    public CustomFieldValueString(CustomFieldDefinition def, Object val) {
        super(def);
        this.setValue(val);
    }

    public Object getValue() {
        return this.value;
    }
    public void setValue(Object obj) {
        if (obj == null) {
            this.value = null;
        } else {
            this.value = obj.toString();
        }
    }
}

