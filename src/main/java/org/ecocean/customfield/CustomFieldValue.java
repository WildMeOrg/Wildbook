package org.ecocean.customfield;

public abstract class CustomFieldValue implements java.io.Serializable {
    private int id;
    private CustomFieldDefinition definition = null;

    public CustomFieldValue() {
    }
    public CustomFieldValue(CustomFieldDefinition def) {
        this.definition = def;
    }
    public CustomFieldValue(CustomFieldDefinition def, Object val) {
        this.definition = def;
        this.setValue(val);
    }

    public CustomFieldDefinition getDefinition() {
        return definition;
    }
    public abstract Object getValue();
    public abstract void setValue(Object obj);

    //public String toString() {  return this.getClass().getName() + ":" + this.id; }
}

