package org.ecocean.api;

public class ApiDeleteCascadeException extends IllegalArgumentException {
    private ApiCustomFields obj;
    public ApiDeleteCascadeException(String message, ApiCustomFields obj) {
        super(message);
        this.obj = obj;
    }
    public ApiCustomFields getObject() {
        return obj;
    }
}

