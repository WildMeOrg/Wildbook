package org.ecocean.ia;

import org.json.JSONObject;

/**
 * Abstraction over the deferred-match enqueue path so
 * {@link org.ecocean.ia.MlServiceProcessor} can be unit-tested
 * without going through the production
 * {@link org.ecocean.servlet.IAGateway#requeueJob} static call. The
 * real implementation wraps {@code requeueJob(payload, true)},
 * which applies the 30s fixed delay (see
 * {@code IAGateway.java:785}); a test double simply captures the
 * published payload for assertions.
 *
 * <p>(Empty-match-prospects design Track 2 C10: testability seam
 * surfaced in Codex round-4 Medium.)</p>
 */
public interface DeferredMatchPublisher {
    /**
     * Publish a deferred-match job payload. Callers are responsible
     * for stamping routing flags (e.g. {@code mlServiceV2: true},
     * {@code deferredMatch: true}) and gate metadata (
     * {@code attempt}, {@code firstDeferredAt},
     * {@code lastGateReason}) into the payload before calling.
     */
    void publish(JSONObject payload);
}
