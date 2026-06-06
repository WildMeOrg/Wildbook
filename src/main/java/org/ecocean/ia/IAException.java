package org.ecocean.ia;

import org.json.JSONArray;
import org.json.JSONObject;

public class IAException extends Exception {
    protected boolean requeue = false;
    protected boolean requeueIncrement = false;
    // protected JSONArray errors = null;

    // ml-service migration v2 (commit #8): optional typed code so callers
    // (e.g. MlServiceProcessor) can classify failures without parsing message
    // strings. Backward-compatible — existing constructors leave code null.
    protected String code;

    public IAException(String message) {
        super(message);
    }

    public IAException(String message, boolean requeue) {
        super(message);
        this.requeue = requeue;
    }

    public IAException(String message, boolean requeue, boolean requeueIncrement) {
        super(message);
        this.requeue = requeue;
        this.requeueIncrement = requeueIncrement;
    }

    public IAException(String code, String message, boolean requeue, boolean requeueIncrement) {
        super(message);
        this.code = code;
        this.requeue = requeue;
        this.requeueIncrement = requeueIncrement;
    }

    public String getCode() {
        return code;
    }

/*
    public IAException(String message, JSONArray errors) {
        super(message);
        this.errors = errors;
    }

    public IAException(String message, JSONObject error) {
        super(message);
        addError(error);
    }
 */
    public boolean shouldRequeue() {
        return requeue;
    }

    public boolean shouldIncrement() {
        return requeueIncrement;
    }
}
