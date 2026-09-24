package org.ecocean.queue;

import java.io.IOException;

import java.lang.Runnable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QueueUtil {
    private static final Logger log = LogManager.getLogger(QueueUtil.class);

    static final String WATCHDOG_THREAD_NAME = "queue-consumer-watchdog";
    static final long WATCHDOG_INTERVAL_SECONDS = 30;
    // A consumer that keeps dying is restarted with exponential backoff: the first restart is
    // immediate, then 30s, 60s, 120s, ... capped at 30 minutes. FileQueue marks a message
    // .complete BEFORE handling it, so every restart that dies again costs one message; the
    // backoff bounds that loss (at the cap, about two messages per hour per worker) while a
    // persistent fault lasts. There is deliberately NO give-up threshold: a supervisor that gives
    // up recreates the silent dead queue this exists to prevent. A crash loop is surfaced instead,
    // loudly, through error.log and the wildbook_queue_consumer_consecutive_deaths gauge.
    static final long RESTART_BACKOFF_BASE_MILLIS = 30000L;
    static final long RESTART_BACKOFF_MAX_MILLIS = 30L * 60L * 1000L;
    // a consumer that ran this long after its last restart is healthy again: backoff resets
    static final long HEALTHY_RESET_MILLIS = 10L * 60L * 1000L;

    // Guards consumer registration, watchdog start/stop, restarts and cleanup, so a restart can
    // never race startup or undeploy. `slots` is copy-on-write so status reads need no lock.
    private static final Object LIFECYCLE = new Object();
    // Serializes whole cleanups, so two overlapping ones cannot interleave; held across the awaits,
    // which LIFECYCLE must not be (the watchdog takes LIFECYCLE and cleanup waits for it).
    private static final Object CLEANUP = new Object();
    private static final List<ScheduledExecutorService> runningSES =
        new ArrayList<ScheduledExecutorService>();
    private static final List<ConsumerSlot> slots = new CopyOnWriteArrayList<ConsumerSlot>();
    private static ScheduledExecutorService watchdog = null;
    private static volatile boolean stopping = false;

    // test seams: the backoff clock, and the watchdog interval (tests run checks by hand)
    static volatile LongSupplier clock = System::currentTimeMillis;
    static volatile long watchdogIntervalSeconds = WATCHDOG_INTERVAL_SECONDS;

    // FileQueue.consume() holds this across reserving its directory AND registering the consumers,
    // so cleanup cannot release the reservation in between
    static Object lifecycleLock() {
        return LIFECYCLE;
    }

    // One consumer worker: its executor, its current periodic task, and supervision state.
    static final class ConsumerSlot {
        final Queue queue;
        final ScheduledExecutorService exec;
        volatile ScheduledFuture<?> future;
        volatile long lastTickMillis;
        volatile long lastRestartMillis;
        volatile int restarts = 0;
        volatile int consecutiveDeaths = 0;
        volatile long nextRestartAllowedMillis = 0;
        volatile String lastDeathCause = null;
        // the dead future whose death has already been recorded (so it is logged once)
        volatile ScheduledFuture<?> deathRecordedFor = null;

        ConsumerSlot(Queue queue, ScheduledExecutorService exec) {
            this.queue = queue;
            this.exec = exec;
            long now = clock.getAsLong();
            this.lastTickMillis = now;
            this.lastRestartMillis = now;
        }
    }

    /** Read-only view of one consumer worker, for metrics and admin pages. */
    public static final class ConsumerStatus {
        private final String queueName;
        private final boolean alive;
        private final boolean stopped;
        private final long secondsSinceLastTick;
        private final int restarts;
        private final int consecutiveDeaths;
        private final String lastDeathCause;

        ConsumerStatus(String queueName, boolean alive, boolean stopped, long secondsSinceLastTick,
            int restarts, int consecutiveDeaths, String lastDeathCause) {
            this.queueName = queueName;
            this.alive = alive;
            this.stopped = stopped;
            this.secondsSinceLastTick = secondsSinceLastTick;
            this.restarts = restarts;
            this.consecutiveDeaths = consecutiveDeaths;
            this.lastDeathCause = lastDeathCause;
        }

        public String getQueueName() {
            return queueName;
        }

        /** scheduled and not dead; NOT proof of progress (see getSecondsSinceLastTick) */
        public boolean isAlive() {
            return alive;
        }

        /** intentionally stopped (STOP file / SHUTDOWN message) or torn down by cleanup */
        public boolean isStopped() {
            return stopped;
        }

        public long getSecondsSinceLastTick() {
            return secondsSinceLastTick;
        }

        public int getRestarts() {
            return restarts;
        }

        /** deaths since the consumer last ran healthily; above 1 means it is crash-looping */
        public int getConsecutiveDeaths() {
            return consecutiveDeaths;
        }

        public String getLastDeathCause() {
            return lastDeathCause;
        }

        @Override public String toString() {
            return queueName + "[alive=" + alive + ", stopped=" + stopped + ", sinceTick=" +
                       secondsSinceLastTick + "s, restarts=" + restarts + "]";
        }
    }

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
        synchronized (LIFECYCLE) {
            if (stopping)
                throw new IOException("QueueUtil is shutting down; not starting consumers for " +
                        queue);
            final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(n + 1);
            List<ConsumerSlot> started = new ArrayList<ConsumerSlot>();
            try {
                for (int w = 0; w < n; w++) {
                    ConsumerSlot slot = new ConsumerSlot(queue, schedExec);
                    slot.future = schedule(slot);
                    started.add(slot);
                }
                ensureWatchdog();
            } catch (Throwable t) {
                // roll back a partial start: no worker may run untracked
                schedExec.shutdownNow();
                throw new IOException("failed to start consumers for " + queue, t);
            }
            slots.addAll(started);
            runningSES.add(schedExec);
        }
        System.out.println("---- " + queue.toString() + " started " + n + " consumer worker(s) ----");
    }

    private static ScheduledFuture<?> schedule(ConsumerSlot slot) {
        return slot.exec.scheduleWithFixedDelay(newConsumerRunnable(slot),
                1, // initial delay
                1, // period delay *after* execution finishes
                TimeUnit.SECONDS);
    }

    // One consumer poll loop. Each call returns a fresh Runnable with its own `count`, so multiple
    // workers on the same queue do not share mutable state.
    //
    // Resilience contract: this runs via scheduleWithFixedDelay, whose spec SILENTLY CANCELS the
    // periodic task if any execution throws — so nothing may escape run() (short of a
    // VirtualMachineError, where hiding it would be worse; the watchdog restarts the consumer
    // afterwards). Only an intentional stop (QueueStopException: operator STOP file, SHUTDOWN
    // message) shuts the executor down; any other failure costs one tick or one message.
    private static Runnable newConsumerRunnable(final ConsumerSlot slot) {
        final Queue queue = slot.queue;
        final ScheduledExecutorService schedExec = slot.exec;
        return new Runnable() {
            int count = 0;
            public void run() {
                try {
                    ++count;
                    slot.lastTickMillis = clock.getAsLong();
                    if (count % 100 == 1)
                        System.out.println("==== " + queue.toString() + " run [count " + count +
                        "] ====");
                    String message = null;
                    try {
                        message = queue.getNext();
                    } catch (QueueStopException stop) {
                        // the ONLY path that stops the consumer; keep the legacy log line —
                        // operators (and runbooks) grep for it
                        System.out.println(":::: " + queue.toString() +
                            " intentional stop: " + stop.getMessage() + " ::::");
                        System.out.println(":::: " + queue.toString() +
                            " shutdown via discontinue signal ::::");
                        schedExec.shutdown();
                        return;
                    } catch (Exception ex) {
                        // transient poll failure (disk hiccup, unreadable spool file): log and
                        // retry next tick; the fixed delay is the backoff
                        log.warn(queue.toString() + " getNext() failed (will retry next tick)",
                            ex);
                        return;
                    }
                    // note message == null means it was read, but there is nothing to handle
                    if (message == null) return;
                    try {
                        queue.messageHandler.handler(message);
                    } catch (Throwable t) {
                        if (t instanceof VirtualMachineError) throw (VirtualMachineError)t;
                        log.error(queue.toString() + " message handler threw; message dropped",
                            t);
                    }
                } catch (Throwable t) {
                    if (t instanceof VirtualMachineError) throw (VirtualMachineError)t;
                    // best-effort logging: toString()/logging on arbitrary objects can themselves
                    // throw, and a throw from HERE silently cancels the periodic task
                    try {
                        log.error(queue.toString() + " consumer tick threw unexpectedly; continuing",
                            t);
                    } catch (Throwable ignored) {}
                }
            }
        };
    }

    // must hold LIFECYCLE
    private static void ensureWatchdog() {
        if (watchdog != null) return;
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, WATCHDOG_THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
        long interval = watchdogIntervalSeconds;
        watchdog.scheduleWithFixedDelay(QueueUtil::watchdogTick, interval, interval,
            TimeUnit.SECONDS);
    }

    // The watchdog is scheduled the same way as the consumers, so it too must never throw —
    // not even a VirtualMachineError: a dead watchdog supervises nothing.
    private static void watchdogTick() {
        try {
            checkConsumersOnce();
        } catch (Throwable t) {
            try {
                log.error("queue consumer watchdog check failed; will retry", t);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Restart any consumer whose periodic task died with an exception (an Error escaping the tick,
     * such as OutOfMemoryError). Never restarts a consumer that was stopped on purpose (its
     * executor is shut down) or a cancelled task (cleanup). Returns the number restarted.
     * Package-private so tests can run a check without waiting for the watchdog interval.
     */
    static int checkConsumersOnce() {
        int restarted = 0;
        synchronized (LIFECYCLE) {
            if (stopping) return 0;
            long now = clock.getAsLong();
            for (ConsumerSlot slot : slots) {
                try {
                    if (checkSlot(slot, now)) restarted++;
                } catch (Throwable t) {
                    try {
                        log.error("queue consumer watchdog failed checking " + slot.queue, t);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return restarted;
    }

    // must hold LIFECYCLE
    private static boolean checkSlot(ConsumerSlot slot, long now) {
        if (slot.exec.isShutdown()) return false;
        ScheduledFuture<?> f = slot.future;
        // still scheduled, or cancelled (a cancelled task is not proof its worker finished)
        if (!f.isDone() || f.isCancelled()) return false;
        // f completed exceptionally: its run() has already returned, so no worker is live and a
        // replacement cannot overlap it
        if (slot.deathRecordedFor != f) {
            slot.deathRecordedFor = f;
            Throwable cause = deathCause(f);
            if (now - slot.lastRestartMillis >= HEALTHY_RESET_MILLIS) slot.consecutiveDeaths = 0;
            slot.consecutiveDeaths++;
            long backoff = restartBackoffMillis(slot.consecutiveDeaths);
            slot.nextRestartAllowedMillis = now + backoff;
            slot.lastDeathCause = (cause == null) ? "unknown" : cause.toString();
            try {
                log.error("Queue consumer " + slot.queue + " DIED (death #" +
                    slot.consecutiveDeaths + " in a row); restarting " +
                    ((backoff == 0) ? "now" : "in " + (backoff / 1000) + "s"), cause);
            } catch (Throwable ignored) {}
        }
        if (now < slot.nextRestartAllowedMillis) return false;
        ScheduledFuture<?> replacement;
        try {
            replacement = schedule(slot);
        } catch (RejectedExecutionException ree) {
            return false; // executor shut down concurrently (intentional stop)
        }
        slot.future = replacement;
        slot.restarts++;
        slot.lastRestartMillis = now;
        slot.lastTickMillis = now;
        try {
            log.warn("Queue consumer " + slot.queue + " restarted by watchdog (restart #" +
                slot.restarts + ")");
        } catch (Throwable ignored) {}
        return true;
    }

    static long restartBackoffMillis(int consecutiveDeaths) {
        if (consecutiveDeaths <= 1) return 0;
        int shift = Math.min(consecutiveDeaths - 2, 20);
        return Math.min(RESTART_BACKOFF_BASE_MILLIS << shift, RESTART_BACKOFF_MAX_MILLIS);
    }

    private static Throwable deathCause(ScheduledFuture<?> f) {
        try {
            f.get();
            return null; // a periodic task never completes normally
        } catch (ExecutionException ee) {
            return ee.getCause();
        } catch (CancellationException ce) {
            return ce;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ie;
        }
    }

    /** Snapshot of every tracked consumer worker. Lock-free; safe to call from a metrics scrape. */
    public static List<ConsumerStatus> consumerStatus() {
        long now = clock.getAsLong();
        List<ConsumerStatus> out = new ArrayList<ConsumerStatus>();
        for (ConsumerSlot s : slots) {
            boolean stopped = s.exec.isShutdown();
            ScheduledFuture<?> f = s.future;
            boolean alive = !stopped && (f != null) && !f.isDone();
            // a consumer running healthily since its last restart is no longer crash-looping
            int deaths = (alive && (now - s.lastRestartMillis >= HEALTHY_RESET_MILLIS)) ? 0
                : s.consecutiveDeaths;
            out.add(new ConsumerStatus(s.queue.queueName, alive, stopped,
                Math.max(0, (now - s.lastTickMillis) / 1000), s.restarts, deaths,
                s.lastDeathCause));
        }
        return out;
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        synchronized (CLEANUP) {
            cleanupSerialized();
        }
    }

    private static void cleanupSerialized() {
        ScheduledExecutorService wd;
        List<ScheduledExecutorService> toStop;
        synchronized (LIFECYCLE) {
            // from here on no consumer may start or be restarted
            stopping = true;
            wd = watchdog;
            toStop = new ArrayList<ScheduledExecutorService>(runningSES);
        }
        boolean allTerminated = true;
        // stop supervision FIRST, so it can never resurrect a consumer being torn down
        if (wd != null) {
            wd.shutdownNow();
            try {
                if (!wd.awaitTermination(20, TimeUnit.SECONDS)) {
                    allTerminated = false;
                    System.out.println("!!! QueueUtil.cleanup() -- watchdog did not terminate");
                }
            } catch (InterruptedException ie) {
                allTerminated = false;
                Thread.currentThread().interrupt();
            }
        }
        for (ScheduledExecutorService ses : toStop) {
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
        for (ConsumerSlot slot : slots) {
            ScheduledFuture<?> f = slot.future;
            if (f != null) f.cancel(true);
        }
        synchronized (LIFECYCLE) {
            if (allTerminated) {
                // Every consumer executor and the watchdog are confirmed stopped (no worker is live),
                // so it is safe to drop tracking and release the single-consumer directory guards; a
                // later start in this classloader can then consume again. If anything survived, we
                // KEEP the guards, the tracking and the stopping flag so no second consumer can start
                // on a directory that still has a live one. (These are classloader statics: a Tomcat
                // redeploy gets fresh ones, so surviving workers there need a JVM restart.)
                runningSES.clear();
                slots.clear();
                watchdog = null;
                FileQueue.releaseAllConsumeGuards();
                stopping = false;
            } else {
                System.out.println("QueueUtil.cleanup() -- not all executors terminated; retaining " +
                    "consume guards and executor tracking to avoid a double consumer");
            }
        }
        System.out.println("QueueUtil.cleanup() finished.");
    }
}
