package org.ecocean.queue;

import java.io.IOException;

import java.lang.Runnable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class QueueUtil {
    private static List<ScheduledExecutorService> runningSES = new ArrayList<ScheduledExecutorService>();
    private static List<ScheduledFuture> runningSF = new ArrayList<ScheduledFuture>();

    public static Queue getBest(String context, String name)
    throws IOException {
        if (!FileQueue.isAvailable(context)) return null;
        FileQueue.init(context);
        return new FileQueue(name);
    }

    // Clamp a configured worker count to a safe effective value. Fails closed to 1 when the queue
    // filesystem does not support atomic moves (see FileQueue.supportsAtomicMove), so a misconfigured
    // deployment can never run multiple consumers that might double-claim a message.
    public static int effectiveWorkers(int configured, boolean atomicMoveSupported) {
        if (!atomicMoveSupported) return 1;
        return Math.max(1, Math.min(configured, 8));
    }

    // helper method for backgrounding queue consumers who dont background themselves.
    // Package-private ON PURPOSE (like backgroundWithWorkers): consumers must start via
    // FileQueue.consume(...), which holds the single-start / atomic-move guards.
    static void background(final Queue queue)
    throws IOException {
        backgroundWithWorkers(queue, 1);
    }

    // Background `workers` concurrent consumers for the queue. workers==1 reproduces the original
    // single-consumer behavior exactly. Concurrent consumers are safe only when the queue's getNext()
    // claims each message atomically (FileQueue uses ATOMIC_MOVE). Package-private ON PURPOSE: the
    // atomic-move gate lives in FileQueue.consume(handler,int); no external caller may start N>1
    // consumers without going through that gate.
    static void backgroundWithWorkers(final Queue queue, int workers)
    throws IOException {
        final int n = Math.max(1, workers);
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(n + 1);
        for (int w = 0; w < n; w++) {
            ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(
                newConsumerRunnable(queue, schedExec),
                1, // initial delay
                1, // period delay *after* execution finishes
                TimeUnit.SECONDS);
            runningSF.add(schedFuture);
        }
        runningSES.add(schedExec);

        System.out.println("---- about to awaitTermination() (" + n + " worker(s)) ----");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: queue interrupted! " + ex.toString());
        }
        System.out.println("==== schedExec.shutdown() called, apparently");
    }

    // One consumer poll loop. Each call returns a fresh Runnable with its own `count`, so multiple
    // workers on the same queue do not share mutable state. Logic is unchanged from the original
    // single-consumer implementation.
    private static Runnable newConsumerRunnable(final Queue queue,
        final ScheduledExecutorService schedExec) {
        return new Runnable() {
            int count = 0;
            public void run() {
                ++count;
                String message = null;
                boolean cont = true;
                try {
                    message = queue.getNext();
                } catch (Exception ex) {
                    System.out.println(queue.toString() + " getNext() got an exception; halting: " +
                    ex.toString());
                    cont = false;
                }
                if (count % 100 == 1)
                    System.out.println("==== " + queue.toString() + " run [count " + count +
                    "]; queue=" + queue.toString() + "; continue = " + cont + " ====");
                boolean ok = true;
                // note message == null means it was read, but there is nothign to handle
                if (cont && (message != null)) {
                    try {
                        ok = queue.messageHandler.handler(message);
                    } catch (IOException ioex) {
                        System.out.println("WARNING: swallowed IOException from message handler: " +
                        ioex.toString());
                    }
                }
// System.out.println("count=" + count + "; handled-ok=" + ok + "; cont=" + cont + "; msg=" + message);
                if (!cont) {
                    System.out.println(":::: " + queue.toString() +
                    " shutdown via discontinue signal ::::");
                    schedExec.shutdown();
                }
            }
        };
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        boolean allTerminated = true;
        for (ScheduledExecutorService ses : runningSES) {
            ses.shutdown();
            try {
                // If it doesn't quiesce on shutdown(), force with shutdownNow(); only then is a
                // still-running executor a real leak. (The prior logic was inverted: it forced only
                // AFTER a clean termination and reported "did not terminate" when it actually had.)
                if (!ses.awaitTermination(20, TimeUnit.SECONDS)) {
                    ses.shutdownNow();
                    if (!ses.awaitTermination(20, TimeUnit.SECONDS)) {
                        allTerminated = false;
                        System.out.println(
                            "!!! QueueUtil.cleanup() -- ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                ses.shutdownNow();
                allTerminated = false; // could not confirm termination
                Thread.currentThread().interrupt();
            }
        }
        for (ScheduledFuture sf : runningSF) {
            sf.cancel(true);
        }
        if (allTerminated) {
            // Every consumer executor is confirmed stopped (no worker is live), so it is safe to drop
            // tracking and release the single-consumer directory guards; an in-place redeploy can
            // then consume again. If a worker survived, we KEEP both the guards and the tracking so a
            // redeploy cannot start a second consumer on a directory that still has a live consumer.
            runningSES.clear();
            runningSF.clear();
            FileQueue.releaseAllConsumeGuards();
        } else {
            System.out.println("QueueUtil.cleanup() -- not all executors terminated; retaining " +
                "consume guards and executor tracking to avoid a double consumer on redeploy");
        }
        System.out.println("QueueUtil.cleanup() finished.");
    }
}
