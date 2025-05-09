package org.ecocean.api.bulk;

import org.ecocean.api.ApiException;

public class BulkValidatorException extends ApiException {
    public static String TYPE_UNKNOWN_FIELDNAME = "UNKNOWN_FIELDNAME";
    public static String TYPE_INVALID_VALUE = "INVALID_VALUE";

    private String type = TYPE_INVALID_VALUE;

    public BulkValidatorException() {
        super("unknown");
    }

    public BulkValidatorException(String message, String errorCode) {
        super(message, errorCode);
    }

    public BulkValidatorException(String message, String errorCode, String type) {
        super(message, errorCode);
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public boolean isType(String testType) {
        if (type == null) return false;
        return type.equals(testType);
    }

    public boolean treatAsWarning(boolean badFieldnamesAreWarnings) {
        if (badFieldnamesAreWarnings && isType(TYPE_UNKNOWN_FIELDNAME)) return true;
        return false;
    }
}
