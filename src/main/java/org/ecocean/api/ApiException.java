package org.ecocean.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class ApiException extends Exception {
    public static String ERROR_RETURN_CODE_REQUIRED = "REQUIRED";
    public static String ERROR_RETURN_CODE_INVALID = "INVALID";
    public static String ERROR_RETURN_CODE_FORBIDDEN = "FORBIDDEN";
    public static String ERROR_RETURN_CODE_UNKNOWN = "UNKNOWN";

    protected JSONArray errors = null;

    public ApiException(String message) {
        super(message);
        addError(message);
    }

    public ApiException(String message, String errorCode) {
        super(message);
        addError(message, errorCode);
    }

    public ApiException(String message, JSONArray errors) {
        super(message);
        this.errors = errors;
    }

    public ApiException(String message, JSONObject error) {
        super(message);
        addError(error);
    }

    public JSONArray getErrors() {
        return errors;
    }

    public void addError(String message) {
        addError(message, null);
    }

    public void addError(String message, String code) {
        if (message == null) return;
        if (code == null) code = ERROR_RETURN_CODE_UNKNOWN;
        JSONObject err = new JSONObject();
        err.put("code", code);
        err.put("details", message);
        this.addError(err);
    }

    public void addError(JSONObject err) {
        if (err == null) return;
        if (this.errors == null) this.errors = new JSONArray();
        this.errors.put(err);
    }
}
