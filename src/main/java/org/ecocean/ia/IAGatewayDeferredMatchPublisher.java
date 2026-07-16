package org.ecocean.ia;

import org.ecocean.servlet.IAGateway;
import org.json.JSONObject;

/**
 * Production {@link DeferredMatchPublisher} that re-queues
 * deferred-match payloads through
 * {@link IAGateway#requeueJob(JSONObject, boolean)} with
 * {@code increment=true} so the 30s fixed delay applies
 * (IAGateway.java:785). Calling {@code addToDetectionQueue}
 * directly would publish immediately and hot-loop.
 *
 * <p>(Empty-match-prospects design Track 2 C11 — Codex round-4
 * Blocker: the deferred enqueue must explicitly use
 * {@code requeueJob(payload, true)}, not just stamp
 * {@code __queueRetries} into the JSON.)</p>
 */
public final class IAGatewayDeferredMatchPublisher implements DeferredMatchPublisher {
    @Override
    public void publish(JSONObject payload) {
        IAGateway.requeueJob(payload, true);
    }
}
