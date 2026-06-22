package org.ecocean.ia;

import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.RestClient;
import org.ecocean.Util;

public class WbiaQueueUtil {
    // Measurement static values
    private static volatile JSONObject wbiaQueue = new JSONObject();
    private static String cacheName = "wbiaQueue";

    // Flag to prevent cache stampede - only one thread can refresh at a time
    private static final AtomicBoolean isReloading = new AtomicBoolean(false);

    // Use volatile for thread-safe reads without synchronization
    private static volatile int numJobs = 0;
    private static volatile int numCompletedJobs = 0;
    private static volatile int numWorkingJobs = 0;
    private static volatile int numQueuedJobs = 0;
    private static volatile int numErrorJobs = 0;
    private static volatile int numDetectionJobs = 0;
    private static volatile int numIDJobs = 0;
    private static volatile int sizeIDJobQueue = 0;
    private static volatile int sizeDetectionJobQueue = 0;

    /**
     * Checks if cache needs refresh and reloads if necessary.
     * Uses AtomicBoolean to prevent cache stampede - only one thread will
     * actually perform the refresh, others will return immediately with
     * the last known (stale but valid) values.
     */
    private static void reloadIfNeeded(boolean forceRefresh) {
        String context = "context0";

        // Quick check if cache is still valid (without acquiring lock)
        try {
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            CachedQuery existingCache = qc.getQueryByName(cacheName);

            // If cache is valid and not forcing refresh, use cached value
            if (!forceRefresh && existingCache != null &&
                System.currentTimeMillis() < existingCache.getNextExpirationTimeout()) {
                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
                return;
            }
        } catch (Exception e) {
            // If we can't check cache, proceed to try refresh
        }

        // Cache expired or doesn't exist - try to acquire the reload lock
        // compareAndSet returns true only if the value was false and we set it to true
        if (!isReloading.compareAndSet(false, true)) {
            // Another thread is already reloading - return immediately with stale data
            // The stale data is still valid (just expired), so it's safe to return
            return;
        }

        // We acquired the lock - we're responsible for refreshing
        try {
            // Double-check cache validity (another thread may have just refreshed)
            QueryCache qc = QueryCacheFactory.getQueryCache(context);
            CachedQuery existingCache = qc.getQueryByName(cacheName);
            if (!forceRefresh && existingCache != null &&
                System.currentTimeMillis() < existingCache.getNextExpirationTimeout()) {
                wbiaQueue = Util.toggleJSONObject(existingCache.getJSONSerializedQueryResult());
                return;
            }

            // Save current values in case of error
            int e_numJobs = numJobs;
            int e_numCompletedJobs = numCompletedJobs;
            int e_numWorkingJobs = numWorkingJobs;
            int e_numQueuedJobs = numQueuedJobs;
            int e_numErrorJobs = numErrorJobs;
            int e_numDetectionJobs = numDetectionJobs;
            int e_numIDJobs = numIDJobs;
            int e_sizeIDJobQueue = sizeIDJobQueue;
            int e_sizeDetectionJobQueue = sizeDetectionJobQueue;

            try {
                URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
                wbiaQueue = Util.toggleJSONObject(RestClient.get(wbiaQueueUrl, 90000));
                CachedQuery cq = new CachedQuery(cacheName, Util.toggleJSONObject(wbiaQueue));
                cq.nextExpirationTimeout = System.currentTimeMillis() + 120000;
                qc.addCachedQuery(cq);

                // Temporary variables for atomic update
                int t_numJobs = 0;
                int t_numCompletedJobs = 0;
                int t_numWorkingJobs = 0;
                int t_numQueuedJobs = 0;
                int t_numErrorJobs = 0;
                int t_numDetectionJobs = 0;
                int t_numIDJobs = 0;
                int t_sizeIDJobQueue = 0;
                int t_sizeDetectionJobQueue = 0;

                JSONObject inspectMe = wbiaQueue.getJSONObject("response").getJSONObject(
                    "json_result");
                Iterator<String> keys = inspectMe.keys();
                while (keys.hasNext()) {
                    String jobID = keys.next();
                    t_numJobs++;
                    JSONObject job = inspectMe.getJSONObject(jobID);
                    boolean working = false;
                    boolean queued = false;
                    if (job.getString("status").equals("completed")) t_numCompletedJobs++;
                    if (job.getString("status").equals("working")) {
                        t_numWorkingJobs++;
                        working = true;
                    }
                    if (job.getString("status").equals("queued")) {
                        t_numQueuedJobs++;
                        queued = true;
                    }
                    if (job.getString("status").equals("error")) t_numErrorJobs++;
                    if (job.getString("function").startsWith("start_detect")) {
                        t_numDetectionJobs++;
                        if (working || queued) t_sizeDetectionJobQueue++;
                    }
                    if (job.getString("function").startsWith("start_identify")) {
                        t_numIDJobs++;
                        if (working || queued) t_sizeIDJobQueue++;
                    }
                }

                // Update all values atomically (volatile writes)
                numJobs = t_numJobs;
                numCompletedJobs = t_numCompletedJobs;
                numWorkingJobs = t_numWorkingJobs;
                numQueuedJobs = t_numQueuedJobs;
                numErrorJobs = t_numErrorJobs;
                numDetectionJobs = t_numDetectionJobs;
                numIDJobs = t_numIDJobs;
                sizeIDJobQueue = t_sizeIDJobQueue;
                sizeDetectionJobQueue = t_sizeDetectionJobQueue;

            } catch (java.net.SocketTimeoutException timeout_e) {
                timeout_e.printStackTrace();
                // Keep old values on error
                numJobs = e_numJobs;
                numCompletedJobs = e_numCompletedJobs;
                numWorkingJobs = e_numWorkingJobs;
                numQueuedJobs = e_numQueuedJobs;
                numErrorJobs = e_numErrorJobs;
                numDetectionJobs = e_numDetectionJobs;
                numIDJobs = e_numIDJobs;
                sizeIDJobQueue = e_sizeIDJobQueue;
                sizeDetectionJobQueue = e_sizeDetectionJobQueue;
            } catch (Exception e) {
                e.printStackTrace();
                // Keep old values on error
                numJobs = e_numJobs;
                numCompletedJobs = e_numCompletedJobs;
                numWorkingJobs = e_numWorkingJobs;
                numQueuedJobs = e_numQueuedJobs;
                numErrorJobs = e_numErrorJobs;
                numDetectionJobs = e_numDetectionJobs;
                numIDJobs = e_numIDJobs;
                sizeIDJobQueue = e_sizeIDJobQueue;
                sizeDetectionJobQueue = e_sizeDetectionJobQueue;
            }
        } finally {
            // Always release the lock
            isReloading.set(false);
        }
    }

    public static int getSizeIDJobQueue(boolean refresh) {
        reloadIfNeeded(refresh);
        return sizeIDJobQueue;
    }

    public static int getSizeDetectionJobQueue(boolean refresh) {
        reloadIfNeeded(refresh);
        return sizeDetectionJobQueue;
    }

    public static String getStatusWBIAJob(String id, boolean refresh) {
        if (id == null) return null;
        reloadIfNeeded(refresh);
        try {
            JSONObject inspectMe = wbiaQueue.getJSONObject("response").getJSONObject("json_result");
            Iterator<String> keys = inspectMe.keys();
            while (keys.hasNext()) {
                String jobID = keys.next();
                if (id.equals(jobID)) {
                    JSONObject job = inspectMe.getJSONObject(jobID);
                    if (job.getString("status") != null) return job.getString("status");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static int getNumWorkingJobs(boolean refresh) {
        reloadIfNeeded(refresh);
        return numWorkingJobs;
    }

    public static int getNumQueuedJobs(boolean refresh) {
        reloadIfNeeded(refresh);
        return numQueuedJobs;
    }
}
