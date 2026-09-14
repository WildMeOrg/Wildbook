package org.ecocean.cache;

public class QueryCacheFactory {
    private static QueryCache qc;

    public synchronized static QueryCache getQueryCache(String context) {
        try {
            if (qc == null) {
                qc = new QueryCache(context);
                qc.loadQueries();
            }
            return qc;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't instantiate a QueryCache.");
            return null;
        }
    }

    /**
     * Invalidate a named cached query without throwing. Both
     * {@link #getQueryCache(String)} (which can return null on
     * uninitialized contexts) and {@link QueryCache#invalidateByName(String)}
     * (which declares {@code throws IOException}) are wrapped so callers
     * doing best-effort cache busting don't have to repeat the same
     * defensive plumbing.
     */
    public static void safeInvalidate(String context, String cacheName) {
        try {
            QueryCache cache = getQueryCache(context);
            if (cache != null) cache.invalidateByName(cacheName);
        } catch (Exception ex) {
            System.out.println(
                "WARN: QueryCacheFactory.safeInvalidate(" + cacheName + ") failed: " + ex);
        }
    }
}
