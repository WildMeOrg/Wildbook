package org.ecocean.api.bulk;

import org.ecocean.api.ApiException;
import org.json.JSONObject;

public class BulkValidatorException extends ApiException {
    // type is kind of a BulkValidation specificity beyond just the
    // ApiException.code value, added to errors via addTypeToErrors()
    public static String TYPE_UNKNOWN_FIELDNAME = "UNKNOWN_FIELDNAME";
    public static String TYPE_INVALID_VALUE = "INVALID_VALUE";
    public static String TYPE_REQUIRED_VALUE = "REQUIRED_VALUE";
    public static String TYPE_INVALID_SYNONYM = "INVALID_SYNONYM";
    public static String TYPE_INVALID_DUPLICATE = "INVALID_DUPLICATE";

    private String type = TYPE_INVALID_VALUE;

    public BulkValidatorException() {
        super("unknown");
        addTypeToErrors();
    }

    public BulkValidatorException(String message, String errorCode) {
        super(message, errorCode);
        addTypeToErrors();
    }

    public BulkValidatorException(String message, String errorCode, String type) {
        super(message, errorCode);
        this.type = type;
        addTypeToErrors();
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

    public void addTypeToErrors() {
        if (this.errors == null) return;
        for (int i = 0; i < this.errors.length(); i++) {
            if (this.errors.optJSONObject(i) != null)
                this.errors.getJSONObject(i).put("type", this.type);
        }
    }
}
