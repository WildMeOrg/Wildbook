package org.ecocean.auth;

/** Service key failure carrying the HTTP status a servlet should write. */
public class ServiceKeyException extends RuntimeException {
    public final int status;

    public ServiceKeyException(int status, String message) {
        super(message);
        this.status = status;
    }
}
