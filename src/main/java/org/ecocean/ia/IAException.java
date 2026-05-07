package org.ecocean.ia;

import org.json.JSONArray;
import org.json.JSONObject;

public class IAException extends Exception {
    protected boolean requeue = false;
    protected boolean requeueIncrement = false;
    // protected JSONArray errors = null;

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
