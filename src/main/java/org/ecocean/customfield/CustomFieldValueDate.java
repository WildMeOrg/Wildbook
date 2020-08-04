package org.ecocean.customfield;

import java.util.Calendar;

public class CustomFieldValueDate extends CustomFieldValue {
    private String value = null;
    public CustomFieldValueDate() {
    }
    public CustomFieldValueDate(CustomFieldDefinition def) {
        super(def);
    }
    public CustomFieldValueDate(CustomFieldDefinition def, Object val) {
        super(def);
        this.setValue(val);
    }

    public Object getValue() {
        return this.value;
    }
    public void setValue(Object obj) {
        if ((obj == null) || !(obj instanceof String)) {
            this.value = null;
        } else {
            this.value = (String)obj;
        }
    }
}

