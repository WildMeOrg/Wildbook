package org.ecocean.api.bulk;

import org.ecocean.api.ApiException;
import org.json.JSONObject;

public class BulkValidatorException extends ApiException {
    // type is kind of a BulkValidation specificity beyond just the
    // ApiException.code value, added to errors via addToErrors()
    public static String TYPE_UNKNOWN_FIELDNAME = "UNKNOWN_FIELDNAME";
    public static String TYPE_INVALID_VALUE = "INVALID_VALUE";
    public static String TYPE_REQUIRED_VALUE = "REQUIRED_VALUE";
    public static String TYPE_INVALID_SYNONYM = "INVALID_SYNONYM";
    public static String TYPE_INVALID_DUPLICATE = "INVALID_DUPLICATE";

    private String type = TYPE_INVALID_VALUE;
    private String fieldName = null;

    public BulkValidatorException() {
        super("unknown");
        addToErrors();
    }

    public BulkValidatorException(String message, String errorCode) {
        super(message, errorCode);
        addToErrors();
    }

    public BulkValidatorException(String message, String errorCode, String type) {
        super(message, errorCode);
        this.type = type;
        addToErrors();
    }

    public BulkValidatorException(String message, String errorCode, String type, String fieldName) {
        super(message, errorCode);
        this.type = type;
        this.fieldName = fieldName;
        addToErrors();
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

    public void addToErrors() {
        if (this.errors == null) return;
        for (int i = 0; i < this.errors.length(); i++) {
            if (this.errors.optJSONObject(i) != null) {
                this.errors.getJSONObject(i).put("type", this.type);
                if (this.fieldName != null)
                    this.errors.getJSONObject(i).put("fieldName", this.fieldName);
            }
        }
    }
}
