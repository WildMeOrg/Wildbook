package org.ecocean.api.submission;

public class SubmissionException extends RuntimeException {
    public final int status;
    public final String code;
    public SubmissionException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
