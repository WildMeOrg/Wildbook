package org.ecocean.api;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.util.concurrent.Semaphore;
import org.ecocean.OpenSearch;

/**
 * Per-instance concurrency guard for the read-only token API. A remote agent may make an unlimited
 * VOLUME of calls — but only a bounded number execute concurrently, so token traffic cannot starve
 * interactive Wildbook users (token search/kNN share the same OpenSearch as the live UI and the live
 * re-ID matching pipeline). Excess token requests get HTTP 429 + Retry-After and back off.
 *
 * Two independent pools: GENERAL (token search / media-resolve / aggregations) and a smaller
 * SIMILARITY (kNN — the heaviest op). Only token-authenticated requests acquire a permit; interactive
 * session/UI traffic (the users we are protecting) is never throttled.
 *
 * Sizes are node/webapp-wide settings (OpenSearch.properties), read once at class load — restart to
 * resize: tokenApiMaxConcurrent (default 8), tokenApiMaxConcurrentSimilarity (default 2),
 * tokenApiRetryAfterSeconds (default 5). Budgets are additive (up to general+similarity concurrent ops
 * per instance). This bounds OpenSearch contention; it is not a Tomcat/DB flood guard (see the spec).
 */
public final class TokenApiConcurrency {

    public enum Kind { GENERAL, SIMILARITY }

    private static final int GENERAL_MAX =
        atLeast1((Integer) OpenSearch.getConfigurationValue("tokenApiMaxConcurrent", 8));
    private static final int SIMILARITY_MAX =
        atLeast1((Integer) OpenSearch.getConfigurationValue("tokenApiMaxConcurrentSimilarity", 2));
    public static final int RETRY_AFTER_SECONDS =
        atLeast1((Integer) OpenSearch.getConfigurationValue("tokenApiRetryAfterSeconds", 5));

    private static final Semaphore GENERAL_SEM = new Semaphore(GENERAL_MAX);
    private static final Semaphore SIMILARITY_SEM = new Semaphore(SIMILARITY_MAX);

    // Saturation observability. Registered defensively: a re-registration (redeploy / multiple webapp
    // contexts in one JVM) must not fail class init, so on failure we run without these metrics.
    private static final Gauge INFLIGHT;
    private static final Counter REJECTED;
    static {
        Gauge g = null;
        Counter c = null;
        try {
            g = Gauge.build().name("wildbook_token_api_inflight")
                .help("Token API requests currently executing, by pool").labelNames("pool").register();
            c = Counter.build().name("wildbook_token_api_rejected_total")
                .help("Token API requests rejected with 429 because the pool was at capacity, by pool")
                .labelNames("pool").register();
        } catch (RuntimeException alreadyRegistered) {
            g = null;
            c = null;
        }
        INFLIGHT = g;
        REJECTED = c;
    }

    private TokenApiConcurrency() {}

    private static int atLeast1(Integer v) {
        return ((v == null) || (v < 1)) ? 1 : v;
    }

    private static Semaphore sem(Kind k) {
        return (k == Kind.SIMILARITY) ? SIMILARITY_SEM : GENERAL_SEM;
    }

    private static String label(Kind k) {
        return (k == Kind.SIMILARITY) ? "similarity" : "general";
    }

    /**
     * Non-blocking acquire. Returns an {@link Permit} to be closed in a try-with-resources / finally,
     * or {@code null} when the pool is at capacity (the caller should respond 429 with Retry-After).
     */
    public static Permit tryAcquire(Kind k) {
        if (!sem(k).tryAcquire()) {
            metric(() -> { if (REJECTED != null) REJECTED.labels(label(k)).inc(); });
            return null;
        }
        // permit is held; metric updates are best-effort and must never lose the permit
        metric(() -> { if (INFLIGHT != null) INFLIGHT.labels(label(k)).inc(); });
        return new Permit(k);
    }

    private static void metric(Runnable r) {
        try { r.run(); } catch (RuntimeException ignore) { /* metrics are best-effort */ }
    }

    /** Idempotent permit handle — releasing exactly once, safe to close more than once. */
    public static final class Permit implements AutoCloseable {
        private final Kind kind;
        private boolean released = false;

        private Permit(Kind k) {
            this.kind = k;
        }

        @Override public void close() {
            if (released) return;
            released = true;
            sem(kind).release();
            metric(() -> { if (INFLIGHT != null) INFLIGHT.labels(label(kind)).dec(); });
        }
    }

    // --- test seams ---
    static int availablePermits(Kind k) {
        return sem(k).availablePermits();
    }
}
