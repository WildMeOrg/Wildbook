package org.ecocean.api.submission;

/** Bounds pooled connections and image memory before starting expensive intake operations. */
public final class SubmissionResources implements AutoCloseable {
    private static final java.util.concurrent.Semaphore SLOTS = new java.util.concurrent.Semaphore(2);
    private static final java.util.Set<String> OWNERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final String owner;
    private SubmissionResources(String owner) { this.owner = owner; }
    public static SubmissionResources acquire(String owner) {
        if (!OWNERS.add(owner)) throw busy();
        if (!SLOTS.tryAcquire()) { OWNERS.remove(owner); throw busy(); }
        return new SubmissionResources(owner);
    }
    private static SubmissionException busy() { return new SubmissionException(429, "LIMIT_EXCEEDED", "Intake processing busy; retry after five seconds"); }
    @Override public void close() { OWNERS.remove(owner); SLOTS.release(); }
}
