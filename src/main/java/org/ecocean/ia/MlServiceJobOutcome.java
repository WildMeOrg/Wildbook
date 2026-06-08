package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed outcome of a single ml-service queue job, returned by
 * {@code MlServiceProcessor.process(...)}. The seven {@link Kind} values
 * distinguish operationally-different terminal states so the caller can
 * record clear status/statusDetails on the parent Task and react
 * appropriately (e.g. enqueue a deferred match when {@code OK}).
 *
 * <p>Migration plan v2 §commit #8.</p>
 */
public final class MlServiceJobOutcome {

    public enum Kind {
        /** Job persisted at least one annotation/embedding; matching can proceed. */
        OK,
        /**
         * Job completed but ml-service returned zero detections. Not an error
         * — the asset is genuinely empty / has no detectable subject — but no
         * downstream match work is needed.
         */
        OK_ZERO_DETECTIONS,
        /**
         * The target (Encounter / MediaAsset) was deleted before/during the
         * job. Terminal-drop with no error; the inactivity-timeout watchdog
         * must not flip it to "error".
         */
        STALE,
        /**
         * ml-service returned a response that failed structural validation
         * (malformed embedding length, non-finite floats, missing fields).
         * Non-retryable; mark task error.
         */
        ERROR_VALIDATION,
        /**
         * Network failure that exceeded retry budget or was non-retryable
         * from the start (4xx). Mark task error.
         */
        ERROR_NETWORK,
        /**
         * Database write failed at the persistence step (e.g. FK violation,
         * idempotency-index conflict that wasn't a no-op).
         */
        ERROR_PERSIST,
        /**
         * Transient network error; the queue framework has been told to
         * requeue this job. Caller should not finalize the task — the next
         * worker pass will pick it up.
         */
        REQUEUE
    }

    private final Kind kind;
    private final String code;
    private final String message;
    private final List<String> persistedAnnotationIds;

    private MlServiceJobOutcome(Kind kind, String code, String message,
        List<String> persistedAnnotationIds) {
        this.kind = kind;
        this.code = code;
        this.message = message;
        // Defensive copy + unmodifiable wrap so the caller can't mutate our
        // state after construction (Codex code-review guidance).
        if (persistedAnnotationIds == null || persistedAnnotationIds.isEmpty()) {
            this.persistedAnnotationIds = Collections.emptyList();
        } else {
            this.persistedAnnotationIds = Collections.unmodifiableList(
                new ArrayList<String>(persistedAnnotationIds));
        }
    }

    // --- Factories ---------------------------------------------------------

    public static MlServiceJobOutcome ok(List<String> persistedAnnotationIds) {
        return new MlServiceJobOutcome(Kind.OK, null, null, persistedAnnotationIds);
    }

    public static MlServiceJobOutcome okZeroDetections() {
        return new MlServiceJobOutcome(Kind.OK_ZERO_DETECTIONS, null, null, null);
    }

    public static MlServiceJobOutcome stale(String reason) {
        return new MlServiceJobOutcome(Kind.STALE, "STALE", reason, null);
    }

    public static MlServiceJobOutcome validationError(String code, String message) {
        return new MlServiceJobOutcome(Kind.ERROR_VALIDATION,
            code == null ? "INVALID" : code, message, null);
    }

    public static MlServiceJobOutcome networkError(String code, String message) {
        return new MlServiceJobOutcome(Kind.ERROR_NETWORK,
            code == null ? "NETWORK" : code, message, null);
    }

    public static MlServiceJobOutcome persistError(String code, String message) {
        return new MlServiceJobOutcome(Kind.ERROR_PERSIST,
            code == null ? "PERSIST" : code, message, null);
    }

    public static MlServiceJobOutcome requeue() {
        return new MlServiceJobOutcome(Kind.REQUEUE, null, null, null);
    }

    // --- Accessors ---------------------------------------------------------

    public Kind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getPersistedAnnotationIds() {
        return persistedAnnotationIds;
    }

    /** True iff this outcome represents a terminal error (not OK*, STALE, or REQUEUE). */
    public boolean isError() {
        return kind == Kind.ERROR_VALIDATION
            || kind == Kind.ERROR_NETWORK
            || kind == Kind.ERROR_PERSIST;
    }
}
