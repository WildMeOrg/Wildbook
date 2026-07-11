package org.ecocean.ia;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs bulk re-identification's per-encounter units concurrently. Each encounter's identification
 * (subParentTask + IA.intakeAnnotations, which for a vector config matches inline) is already a
 * self-contained unit in the serial driver (resendBulkImportID.jsp); this just runs N of them at
 * once, each on its OWN Shepherd. Gated by iaMatchThreads (the driver runs its original serial loop
 * when threads &lt;= 1, so the default path is unchanged).
 *
 * <p>Design notes (Codex-reviewed):
 * <ul>
 * <li>Workers do NOT touch the shared root Task — the driver only reports the root's id and links
 *     child&harr;subParent locally, so there is no cross-thread parent mutation.</li>
 * <li>The executor is APP-SCOPED (one shared bounded pool), so two concurrent bulk-import requests
 *     cannot exceed the configured worker count in aggregate (the DB-pool bound stays honest).</li>
 * <li>Per-encounter transaction semantics MATCH the serial driver exactly (IA.intakeAnnotations /
 *     storeNewTask commit internally): this is NOT atomic per encounter, but it is no worse than the
 *     current serial path. A failed encounter is isolated and reported; it does not affect others.</li>
 * <li>Cancellation relies on the underlying client timeouts (OpenSearch socket timeout, ml-service
 *     read timeout); each worker Shepherd is closed in a finally.</li>
 * </ul>
 */
public class ParallelIdentify {
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(0);
    private static volatile ExecutorService POOL = null;
    // The AUTHORITATIVE global concurrency bound. Every match (whether run by a pool thread OR by a
    // CallerRunsPolicy caller thread when the queue is full) must hold a permit, so the number of
    // concurrent Shepherd transactions / DB connections never exceeds this across all requests.
    private static volatile Semaphore GLOBAL = null;
    private static int poolSize = 0;

    /** Clamp to [1, min(16, cpu cores)] — measured throughput regresses past ~cores. */
    public static int clampThreads(int threads) {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(threads, Math.min(16, cores)));
    }

    // App-scoped bounded pool, sized on first use (a config change needs a restart to take effect —
    // documented on iaMatchThreads). Daemon threads so this never blocks JVM shutdown. Bounded queue
    // + CallerRunsPolicy: if concurrent requests overfill the queue, the submitting request thread
    // runs the task inline (backpressure) rather than growing memory unbounded.
    private static synchronized ExecutorService pool(int threads) {
        if (POOL == null) {
            poolSize = clampThreads(threads);
            GLOBAL = new Semaphore(poolSize);
            ThreadFactory tf = new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ParallelIdentify-" + THREAD_SEQ.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            };
            POOL = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2048), tf,
                new ThreadPoolExecutor.CallerRunsPolicy());
        }
        return POOL;
    }

    /** Shut the shared pool down (call from app lifecycle shutdown to avoid classloader retention). */
    public static synchronized void shutdown() {
        if (POOL == null) return;
        // GRACEFUL: shutdown() lets queued tasks still RUN (so their callers' f.get() completes and
        // never hangs); only force after a grace period. shutdownNow() alone would drain queued
        // FutureTasks without completing them, hanging any caller waiting on f.get().
        POOL.shutdown();
        try {
            if (!POOL.awaitTermination(10, TimeUnit.SECONDS)) forceStop();
        } catch (InterruptedException ie) {
            forceStop();
            Thread.currentThread().interrupt();
        }
        POOL = null;
        GLOBAL = null;
    }

    // shutdownNow() drains queued tasks WITHOUT completing them; cancel each so any caller blocked in
    // future.get() gets a CancellationException instead of hanging forever.
    private static void forceStop() {
        java.util.List<Runnable> dropped = POOL.shutdownNow();
        for (Runnable r : dropped) {
            if (r instanceof Future) ((Future<?>) r).cancel(true);
        }
    }

    /**
     * Identify each encounter (by id) concurrently. Returns one JSON entry per encounter that
     * produced work: {topTaskId, childTaskId, encounterId} on success, {encounterId, error} on
     * failure; encounters with no valid annotations are omitted (matching the serial driver).
     * rootTaskId is echoed as topTaskId only (workers never load/modify the root).
     */
    public static JSONArray identifyEncounters(String context, String rootTaskId,
        List<String> encounterIds, JSONObject taskParameters, int threads) {
        JSONArray jobs = new JSONArray();
        if ((encounterIds == null) || encounterIds.isEmpty()) return jobs;
        // de-dup so the same encounter is never identified twice concurrently
        LinkedHashSet<String> encIds = new LinkedHashSet<String>();
        for (String e : encounterIds) {
            if ((e != null) && (e.trim().length() > 0)) encIds.add(e.trim());
        }
        final String rootId = rootTaskId;
        final String ctx = context;
        // per-call task-parameter template string (each worker parses its own copy — never share the
        // JSONObject, since downstream query building has historically mutated it)
        final String paramsStr = (taskParameters == null) ? null : taskParameters.toString();

        ExecutorService exec = pool(threads);
        List<Future<JSONObject>> futs = new ArrayList<Future<JSONObject>>();
        for (final String encId : encIds) {
            futs.add(exec.submit(new java.util.concurrent.Callable<JSONObject>() {
                public JSONObject call() {
                    return processOne(ctx, rootId, encId, paramsStr);
                }
            }));
        }
        // wait for all (bounded by the underlying client timeouts); a running worker is never
        // reported as a failure — we always collect its real outcome.
        for (Future<JSONObject> f : futs) {
            try {
                JSONObject j = f.get();
                if (j != null) jobs.put(j);
            } catch (InterruptedException ie) {
                // request thread interrupted (e.g. client aborted): cancel outstanding work and
                // report the run as incomplete rather than a silent partial success.
                Thread.currentThread().interrupt();
                for (Future<JSONObject> pending : futs) pending.cancel(true);
                JSONObject inc = new JSONObject();
                inc.put("error", "interrupted; run incomplete");
                jobs.put(inc);
                System.out.println("ParallelIdentify.identifyEncounters interrupted; run incomplete");
                break;
            } catch (Exception ex) {
                System.out.println("ParallelIdentify future failed: " + ex);
            }
        }
        return jobs;
    }

    // One encounter's identification on its OWN Shepherd. Mirrors the serial driver's per-encounter
    // steps exactly (including the internal commits via storeNewTask/updateDBTransaction).
    private static JSONObject processOne(String context, String rootTaskId, String encId,
        String paramsStr) {
        // Hold a global permit for the whole DB/match unit (acquired BEFORE opening a Shepherd, so we
        // never hold a connection while waiting). This caps concurrent matches to poolSize even when a
        // CallerRunsPolicy caller thread runs this inline.
        Semaphore permits = GLOBAL;
        if (permits != null) {
            try {
                permits.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                JSONObject job = new JSONObject();
                job.put("encounterId", encId);
                job.put("error", "interrupted before start");
                return job;
            }
        }
        // Shepherd setup is INSIDE the try so the finally always releases the permit even if opening
        // the Shepherd / beginning the transaction throws.
        Shepherd ws = null;
        try {
            ws = new Shepherd(context);
            ws.setAction("ParallelIdentify.processOne." + encId);
            ws.beginDBTransaction();
            Encounter enc = ws.getEncounter(encId);
            if (enc == null) { ws.rollbackDBTransaction(); return null; }
            JSONObject params = (paramsStr == null) ? new JSONObject() : new JSONObject(paramsStr);
            Task subParentTask = new Task();
            subParentTask.setParameters(params);
            ws.storeNewTask(subParentTask);
            ws.updateDBTransaction();

            List<Annotation> matchMeAnns = new ArrayList<Annotation>();
            if (enc.getAnnotations() != null) {
                for (Annotation queryAnn : enc.getAnnotations()) {
                    if (IBEISIA.validForIdentification(queryAnn)) matchMeAnns.add(queryAnn);
                }
            }
            if (matchMeAnns.isEmpty()) { ws.commitDBTransaction(); return null; }

            Task childTask = IA.intakeAnnotations(ws, matchMeAnns, subParentTask, false);
            ws.storeNewTask(childTask);
            ws.updateDBTransaction();
            subParentTask.addChild(childTask);
            ws.updateDBTransaction();

            JSONObject job = new JSONObject();
            job.put("topTaskId", rootTaskId);
            job.put("childTaskId", childTask.getId());
            job.put("encounterId", encId);
            return job;
        } catch (Exception ex) {
            System.out.println("ParallelIdentify.processOne error on encounter " + encId + ": " + ex);
            ex.printStackTrace();
            JSONObject job = new JSONObject();
            job.put("encounterId", encId);
            job.put("error", ex.toString());
            return job;
        } finally {
            // ALWAYS rollback-then-close: after the success path's last updateDBTransaction() a new
            // empty transaction is active, and closing a PM with an active tx throws (and can leak the
            // connection). rollbackAndClose discards only that empty tx; all internal commits stand.
            if (ws != null) { try { ws.rollbackAndClose(); } catch (Exception e3) {} }
            if (permits != null) permits.release();
        }
    }
}
