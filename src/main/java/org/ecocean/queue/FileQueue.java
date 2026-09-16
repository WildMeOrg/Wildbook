package org.ecocean.queue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.CommonConfiguration;
import org.ecocean.Util;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.util.Arrays;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class FileQueue extends Queue {
    private static String TYPE_NAME = "File";
    private static File queueBaseDir = null;
    private File queueDir = null;
    // True only when more than one consumer thread runs on this queue: concurrent claims MUST be
    // atomic to avoid double-processing. With a single (serial) consumer we keep the original
    // non-atomic rename claim, which works on every filesystem (the concurrency gate would otherwise
    // stall consumption on a filesystem that does not support atomic moves).
    private volatile boolean requireAtomicClaim = false;
    // Guards against a second consume() starting extra worker threads (double the claimers) or
    // resetting requireAtomicClaim while earlier workers are still live. Two levels: `consuming`
    // stops a repeat call on the SAME instance; CONSUMING_DIRS stops a SECOND FileQueue instance
    // (getBest() makes a new one per call) from consuming the SAME directory concurrently. Released
    // only in QueueUtil.cleanup(), after all consumer executors are confirmed terminated, so an
    // in-place redeploy can consume again without ever overlapping a live consumer.
    private static final java.util.Set<String> CONSUMING_DIRS =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean consuming = false;
    private String consumingKey = null;

    private synchronized boolean markConsuming() {
        if (consuming) {
            System.out.println("WARNING: " + this.toString() +
                " consume() already started; ignoring repeat call");
            return false;
        }
        String key;
        try {
            key = (queueDir == null) ? null : queueDir.getCanonicalPath();
        } catch (IOException ex) {
            key = queueDir.getAbsolutePath();
        }
        if ((key != null) && !CONSUMING_DIRS.add(key)) {
            System.out.println("WARNING: " + this.toString() +
                " another consumer is already running on " + key + "; ignoring");
            return false;
        }
        consumingKey = key;
        consuming = true;
        return true;
    }

    public static boolean isAvailable(String context) {
        return true; 
    }

    public FileQueue(final String name)
    throws IOException {
        super(name);
        if (queueBaseDir == null)
            throw new IOException("FileQueue.init() has not yet been called!");
        this.type = TYPE_NAME;
        queueDir = new File(queueBaseDir, name); 
        if (!queueDir.exists() || !queueDir.isDirectory()) {
            boolean ok = queueDir.mkdirs();
            if (!ok) throw new IOException("FileQueue failed to create " + queueDir.toString());
        }
    }

    public static synchronized void init(String context)
    throws IOException {
        File qd = setQueueDir(context);

        if (qd == null) throw new IOException("ERROR: unable to FileQueue.setQueueDir()");
        System.out.println("[INFO] FileQueue.init(" + context + ") complete");
    }

    public static synchronized File setQueueDir(String context)
    throws IOException {
        if (queueBaseDir != null) return queueBaseDir; // hey we have one already!
        String qd = Queue.getProperty(context, "filequeue_basedir");
        if (qd == null) qd = CommonConfiguration.getProperty("ScheduledQueueDir", "context0"); // legacy
        if (qd == null) { // lets try to make one *somewhere*
            queueBaseDir = new File("/tmp/WildbookFileQueue");
            System.out.println("INFO: default (temporary) queueBaseDir being used: " +
                queueBaseDir);
        } else {
            queueBaseDir = new File(qd);
        }
        return queueBaseDir;
    }

    public void publish(String msg)
    throws IOException {
        if (queueDir == null)
            throw new IOException("FileQueue.publish() failed, queueDir is not set");
        String qid = Util.generateUUID();
        File tmpFile = new File(queueDir, "addToQueue-" + qid + ".tmp"); // write to tmp file first so it doesnt (yet) get picked up by queue til done
        PrintWriter qout = new PrintWriter(tmpFile);
        qout.print(msg);
        qout.close();
        tmpFile.renameTo(new File(queueDir, qid));
        System.out.println("INFO: FileQueue.publish() added " + queueDir + " -> " + qid);
    }

    public void consume(final QueueMessageHandler msgHandler)
    throws IOException {
        if (!markConsuming()) return;
        this.messageHandler = msgHandler;
        QueueUtil.background(this);
    }

    // Authoritative concurrency gate: this is the ONLY place worker count is enforced, so no caller
    // (StartupWildbook or otherwise) can start N>1 consumers unsafely. If the queue filesystem does
    // not support atomic moves, the effective count is clamped to 1 (serial, non-atomic claim). Only
    // when the effective count is >1 do we require atomic claims in getNext().
    public void consume(final QueueMessageHandler msgHandler, int workers)
    throws IOException {
        if (!markConsuming()) return;
        this.messageHandler = msgHandler;
        boolean atomicOk = supportsAtomicMove(queueDir);
        int eff = QueueUtil.effectiveWorkers(workers, atomicOk);
        if (eff < workers) {
            String why = atomicOk ? "exceeds max (8)"
                : "queue filesystem does not support atomic moves";
            System.out.println("WARNING: " + this.toString() + " requested " + workers +
                " consumer(s) but " + why + "; running " + eff);
        }
        this.requireAtomicClaim = (eff > 1);
        QueueUtil.backgroundWithWorkers(this, eff);
    }

    public File getQueueDir() {
        return queueDir;
    }

    // Probe whether the given directory's filesystem supports atomic file moves. Concurrent
    // consumers rely on ATOMIC_MOVE to claim a queue file exactly once; on a filesystem that does
    // not support it (some NFS/overlay/NTFS mounts) callers must fall back to a single consumer.
    public static boolean supportsAtomicMove(File dir) {
        if ((dir == null) || !dir.isDirectory()) return false;
        File a = new File(dir, ".atomicmove-probe-" + Thread.currentThread().getId());
        File b = new File(dir, ".atomicmove-probe-" + Thread.currentThread().getId() + ".moved");
        try {
            Files.write(a.toPath(), "probe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.move(a.toPath(), b.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            return false;
        } catch (Exception ex) {
            System.out.println("FileQueue.supportsAtomicMove() probe failed on " + dir + ": " + ex);
            return false;
        } finally {
            a.delete();
            b.delete();
        }
    }

    // Claim src -> dst, returning true on success. Uses an ATOMIC_MOVE when concurrent consumers run
    // (requireAtomicClaim), so exactly one worker can win; otherwise (single serial consumer) uses
    // the original destination-exists check + renameTo, which works on every filesystem. A pre-check
    // on the destination preserves the original "skip if the target already exists" behavior (e.g. a
    // stale .active/.complete from a crashed run) that a bare ATOMIC_MOVE would otherwise overwrite.
    private boolean moveClaim(File src, File dst) {
        if (dst.exists()) {
            System.out.println("WARNING: " + this.toString() + " wanted to create " +
                dst.toString() + " but it exists; skipping");
            return false;
        }
        if (!requireAtomicClaim) {
            if (!src.renameTo(dst)) {
                System.out.println("WARNING: " + this.toString() + " wanted to create " +
                    dst.toString() + " but rename failed; skipping");
                return false;
            }
            return true;
        }
        try {
            Files.move(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (java.nio.file.FileAlreadyExistsException ex) {
            return false; // another worker already claimed it
        } catch (java.nio.file.NoSuchFileException ex) {
            return false; // another worker moved the source out from under us
        } catch (IOException ex) {
            // includes AtomicMoveNotSupportedException; fail closed (never a non-atomic fallback here)
            System.out.println("WARNING: " + this.toString() + " atomic claim failed for " + src +
                ": " + ex);
            return false;
        }
    }

    public String getNext()
    throws IOException {
        if (queueDir == null)
            throw new IOException(this.toString() + " FileQueue.getNext() queueDir is null");
        File nextFile = getNextFile();
        if (nextFile == null) return null; // wait and try again...

        File activeFile = new File(nextFile.toString() + ".active");
        // Claim the file. With concurrent consumers this is an atomic move so only one worker wins;
        // with a single serial consumer it is the original rename. The loser/skip returns null.
        if (!moveClaim(nextFile, activeFile)) {
            return null;
        }
        System.out.println("INFO: " + this.toString() + " successfully engaged file " +
            nextFile.toString() + "; made .active");

        // for now we assume we *only* support json content... fix if you need to, future
        String fcontents = null;
        try {
            fcontents = StringUtils.join(Files.readAllLines(activeFile.toPath(),
                java.nio.charset.Charset.defaultCharset()), "");
        } catch (Exception ex) {
            throw new IOException("ERROR: " + this.toString() + " could not read " + nextFile +
                    ": " + ex.toString());
        }
        File completedFile = new File(nextFile.toString() + ".complete");
        if (!moveClaim(activeFile, completedFile)) {
            System.out.println("WARNING: " + this.toString() + " could not mark " +
                completedFile.toString() + " complete; skipping");
            return null;
        }
        if (this.isConsumerShutdownMessage(fcontents))
            throw new IOException("SHUTDOWN message received");
        return fcontents;
    }

    // current algorithm is "oldest" (FIFO); filename simply must be a valid uuid (with no extension)
    public File getNextFile()
    throws IOException {
        if (queueDir == null) return null;
        if (!queueDir.exists() || !queueDir.isDirectory()) {
            System.out.println("WARNING: " + queueDir +
                " does not exist or is not a directory; skipping");
            return null;
        }
        FileTime oldestTime = null;
        File oldestFile = null;
        File[] dfiles = null;
        try {
            dfiles = queueDir.listFiles();
        } catch (Exception ex) {
            System.out.println("WARNING: could not read directory " + queueDir + ": " +
                ex.toString());
            return null;
        }
        if (dfiles == null) {
            System.out.println("WARNING: " + queueDir +
                " had trouble reading directory contents; skipping");
            return null;
        }
        for (File f : dfiles) {
// System.out.println("queueDir file=" + f);
            // this always wins... so we can just grind everything to a halt
            if (isStopFile(f))
                throw new IOException(this.toString() + " FileQueue STOP FILE found");
            if (f.isDirectory() || !Util.isUUID(f.getName())) continue; // ignore!
            BasicFileAttributes attr = null;
            try {
                attr = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            } catch (java.io.IOException ioe) {
                System.out.println("WARNING: could not read file attributes for " + f + ": " +
                    ioe.toString());
            }
            if (attr == null) continue;
// System.out.println(f.toString() + " time = " + attr.creationTime());
            if ((oldestTime == null) || (oldestTime.compareTo(attr.creationTime()) > 0)) {
                oldestTime = attr.creationTime();
                oldestFile = f;
            }
        }
        return oldestFile;
    }

    private static boolean isStopFile(File file) {
        if (file == null) return false;
        return file.getName().equals("STOP");
    }

    public void shutdown() {
        // Intentionally does NOT release the consume guards: the worker threads are owned by
        // QueueUtil's executors, and releasing here (without stopping them) would let a subsequent
        // consume() start new workers / rewrite requireAtomicClaim while the old workers still run.
        // The guards are cleared in QueueUtil.cleanup(), which stops AND awaits all executors first.
    }

    // Called by QueueUtil.cleanup() ONLY after all consumer executors are shut down and awaited, so
    // no worker is live. Lets an in-place redeploy (contextDestroyed -> contextInitialized in the
    // same JVM) consume these directories again.
    static void releaseAllConsumeGuards() {
        CONSUMING_DIRS.clear();
    }

    @Override public String toString() {
        return super.toString() + " -> " + queueDir;
    }

    public long getQueueSize() {
        try {
            if (queueDir != null) {
                List fileNames = Arrays.asList(queueDir.list())
                        .stream()
                        .filter(x -> x.contains(".complete"))
                        .collect(Collectors.toList());
                int numCompleteFiles = fileNames.size();
                int numFiles = queueDir.listFiles().length;
                return (numFiles - numCompleteFiles);
            }
        } catch (Exception e) {}
        return 0;
    }
}
