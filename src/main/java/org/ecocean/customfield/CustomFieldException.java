package org.ecocean.customfield;

public class CustomFieldException extends Exception {
    private int numValues = -1;
    public CustomFieldException(String message) {
        super(message);
        this.numValues = -1;
    }
    public CustomFieldException(String message, int numValues) {
        super(message);
        this.numValues = numValues;
    }
    public int getNumValues() {
        return numValues;
    }
}

