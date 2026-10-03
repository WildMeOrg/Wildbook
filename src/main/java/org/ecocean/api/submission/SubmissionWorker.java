package org.ecocean.api.submission;

import java.util.*;
import java.util.concurrent.*;
import javax.servlet.ServletContext;

/** Lifecycle-owned, single-thread pilot worker. Database claims coordinate multiple JVMs. */
public final class SubmissionWorker implements AutoCloseable {
    private final ScheduledExecutorService executor;
    private final ServletContext servlet;
    private long lastCleanup;
    private final long startup = System.currentTimeMillis();
    private String replayAfter = "";
    private boolean replayFinished;
    private long lastBusyLog;
    public SubmissionWorker(ServletContext servlet) {
        this.servlet = servlet;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wildbook-submissions"); t.setDaemon(true); return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 10, 10, TimeUnit.SECONDS);
    }
    private void tick() {
        if (!SubmissionPolicy.workerEnabled("context0") || Thread.currentThread().isInterrupted()) return;
        try (SubmissionResources slot = SubmissionResources.acquire("worker:context0")) {
            SubmissionJobs jobs = new SubmissionJobs("context0");
            SubmissionFiles storage = new SubmissionFiles("context0", servlet);
            jobs.reconcileStaleClaims("context0");
            SubmissionProcessing processing = new SubmissionProcessing("context0");
            try { processing.reconcile("context0"); }
            catch (Exception ex) { servlet.log("Submission AI reconciliation deferred; intake continues", ex); }
            if (System.currentTimeMillis() - lastCleanup > 60 * 60 * 1000) {
                lastCleanup = System.currentTimeMillis();
                try { jobs.cleanup("context0", storage); }
                catch (Exception ex) { servlet.log("Submission cleanup deferred; intake continues", ex); }
            }
            String id = jobs.claimNext("context0");
            if (id != null) {
                jobs.execute("context0", id, (draft, sh) -> new SubmissionImporter().execute(draft, draft.getJobId(), sh, storage));
                servlet.log("Submission worker completed attempt: " + id);
            }
            for (String pending : jobs.pendingPostprocessing("context0")) {
                if (Thread.currentThread().isInterrupted()) return;
                try { jobs.postprocess("context0", pending); }
                catch (Exception ex) { holdIndexFailure(jobs, pending); servlet.log("Submission postprocessing requires inspection: " + pending, ex); }
            }
            try {
                for (String pending : processing.pending("context0")) {
                    if (Thread.currentThread().isInterrupted()) return;
                    try { processing.dispatch("context0", pending); }
                    catch (Exception ex) { servlet.log("Submission AI handoff requires inspection: " + pending, ex); }
                }
            } catch (Exception ex) { servlet.log("Submission AI discovery deferred", ex); }
            if (!replayFinished) {
                List<String> replay = jobs.replayBatch("context0", startup, replayAfter);
                for (String pending : replay) {
                    if (Thread.currentThread().isInterrupted()) return;
                    try { jobs.postprocess("context0", pending); }
                    catch (Exception ex) { holdIndexFailure(jobs, pending); servlet.log("Submission index replay requires inspection: " + pending, ex); }
                }
                replayFinished = replay.isEmpty();
                if (!replayFinished) replayAfter = replay.get(replay.size() - 1);
            }
        } catch (SubmissionException busy) {
            if (busy.status == 429 && System.currentTimeMillis() - lastBusyLog > 60 * 1000) {
                servlet.log("Submission worker waiting for intake processing slot"); lastBusyLog = System.currentTimeMillis();
            }
            if (busy.status != 429) servlet.log("Submission worker unavailable: " + busy.code);
        } catch (Exception ex) { servlet.log("Submission worker requires inspection", ex); }
    }
    private void holdIndexFailure(SubmissionJobs jobs, String id) {
        try { jobs.holdFailedIndexing("context0", id); }
        catch (Exception ex) { servlet.log("Submission index failure needs reconciliation: " + id, ex); }
    }
    @Override public void close() {
        executor.shutdownNow();
        try { if (!executor.awaitTermination(15, TimeUnit.SECONDS)) servlet.log("Submission worker still stopping; unfinished claims require reconciliation"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
