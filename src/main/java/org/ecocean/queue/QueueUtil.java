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

    // helper method for backgrounding queue consumers who dont background themselves
    public static void background(final Queue queue)
    throws IOException {
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(2);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            int count = 0;
            public void run() {
                // Outer Throwable catch is the survival boundary for this
                // scheduled consumer. ScheduledExecutorService.scheduleWithFixedDelay
                // silently cancels the future if the Runnable throws an uncaught
                // exception — observed in production when an unexpected
                // RuntimeException/Error escaped the handler and permanently
                // killed the consumer thread with no log line and no restart.
                // Anything inside this method (queue polling, the message
                // handler, logging, shutdown signalling, even toString() calls)
                // must NOT be allowed to propagate out, or the consumer dies.
                try {
                    runOnce();
                } catch (Throwable outer) {
                    // OOM-tolerant: static message first, no concatenation, so
                    // the catch survives even when the heap is exhausted.
                    try {
                        System.err.println(
                            "ERROR: QueueUtil consumer survived an outer Throwable");
                        outer.printStackTrace(System.err);
                    } catch (Throwable ignored) { /* last-resort */ }
                }
            }
            private void runOnce() {
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
                    } catch (Throwable t) {
                        // Per-message survival: log + continue. The outer
                        // catch (Throwable) wraps this block too, but
                        // handling here lets us continue the same iteration
                        // (cont/shutdown logic below) without aborting it.
                        // OOM-tolerant logging: static message first, no
                        // concatenation, then best-effort details.
                        try {
                            System.err.println(
                                "ERROR: QueueUtil swallowed Throwable from message handler; message dropped");
                            t.printStackTrace(System.err);
                        } catch (Throwable ignored) { /* last-resort */ }
                    }
                }
// System.out.println("count=" + count + "; handled-ok=" + ok + "; cont=" + cont + "; msg=" + message);
                if (!cont) {
                    System.out.println(":::: " + queue.toString() +
                    " shutdown via discontinue signal ::::");
                    schedExec.shutdown();
                }
            }
        }, 1, // initial delay
            1, // period delay *after* execution finishes
            TimeUnit.SECONDS);

        runningSES.add(schedExec);
        runningSF.add(schedFuture);

        System.out.println("---- about to awaitTermination() ----");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: queue interrupted! " + ex.toString());
        }
        System.out.println("==== schedExec.shutdown() called, apparently");
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        for (ScheduledExecutorService ses : runningSES) {
            ses.shutdown();
            try {
                if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
                    ses.shutdownNow();
                    if (ses.awaitTermination(20, TimeUnit.SECONDS)) {
                        System.out.println(
                            "!!! QueueUtil.cleanup() -- ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                ses.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        for (ScheduledFuture sf : runningSF) {
            sf.cancel(true);
        }
        System.out.println("QueueUtil.cleanup() finished.");
    }
}
