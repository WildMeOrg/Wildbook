package org.ecocean.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class ApiException extends Exception {
    public static String ERROR_RETURN_CODE_REQUIRED = "REQUIRED";
    public static String ERROR_RETURN_CODE_INVALID = "INVALID";
    public static String ERROR_RETURN_CODE_UNKNOWN = "UNKNOWN";

    private JSONArray errors = null;
    public ApiException(String message) {
        super(message);
        JSONObject err = new JSONObject();
        err.put("code", ERROR_RETURN_CODE_UNKNOWN);
        err.put("details", message);
        this.errors = new JSONArray();
        this.errors.put(err);
    }
    public ApiException(String message, JSONArray errors) {
        super(message);
        this.errors = errors;
    }
    public ApiException(String message, JSONObject error) {
        super(message);
        this.errors = new JSONArray();
        this.errors.put(error);
    }
    public JSONArray getErrors() {
        return errors;
    }
}

