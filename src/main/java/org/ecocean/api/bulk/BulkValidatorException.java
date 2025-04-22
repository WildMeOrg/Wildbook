package org.ecocean.api.bulk;

import org.ecocean.api.ApiException;

public class BulkValidatorException extends ApiException {
    public BulkValidatorException() {
        super("unknown");
    }
    public BulkValidatorException(String message, String errorCode) {
        super(message, errorCode);
    }
}

