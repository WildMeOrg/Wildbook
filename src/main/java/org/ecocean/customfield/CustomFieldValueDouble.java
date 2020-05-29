package org.ecocean.customfield;

public class CustomFieldValueDouble extends CustomFieldValue {
    private Double value = null;
    public CustomFieldValueDouble() {
    }
    public CustomFieldValueDouble(CustomFieldDefinition def) {
        super(def);
    }
    public CustomFieldValueDouble(CustomFieldDefinition def, Object val) {
        super(def);
        this.setValue(val);
    }

    public Object getValue() {
        return this.value;
    }
    public void setValue(Object obj) {
        if ((obj == null) || !(obj instanceof Double)) {
            this.value = null;
        } else {
            this.value = (Double)obj;
        }
    }
}

