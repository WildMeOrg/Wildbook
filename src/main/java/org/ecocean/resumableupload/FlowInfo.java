package org.ecocean.resumableupload;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlowInfo {
    public int flowChunkSize;
    public long flowTotalSize;
    public String flowIdentifier;
    public String flowFilename;
    public String flowRelativePath;

    public static class flowChunkNumber {
        public flowChunkNumber(int number) {
            this.number = number;
        }

        public int number;

        @Override public boolean equals(Object obj) {
            return obj instanceof flowChunkNumber ? ((flowChunkNumber)obj).number ==
                       this.number : false;
        }

        @Override public int hashCode() {
            return number;
        }
    }

    // Chunks uploaded
    // concurrent: flow.js sends several chunks of one file at once (simultaneousUploads)
    public Set<flowChunkNumber> uploadedChunks = ConcurrentHashMap.newKeySet();

    public String flowFilePath;
    /**
     * The validated final destination. Derived up front, NOT by trimming ".temp" off
     * flowFilePath: once that path is canonicalised it may resolve to a name with no ".temp"
     * suffix at all, and trimming five characters from it then lands outside the upload root.
     */
    public String finalFilePath;

    // Chunk writes hold the read side, so several chunks of one file still write in parallel;
    // finalization holds the write side, so it can never overlap a write that is in flight, and
    // any write arriving afterwards sees `finalized` and skips instead of recreating the .temp.
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean finalized = false; // guarded by lifecycle

    /** A chunk write, run while finalization is held off. */
    public interface ChunkWrite {
        void run() throws IOException;
    }

    /**
     * Runs the write unless this upload has already been finalized. Returns false, without running
     * it, if it has -- so a late duplicate chunk can be acknowledged without touching the disk.
     */
    public boolean writeUnlessFinalized(ChunkWrite write)
    throws IOException {
        lifecycle.readLock().lock();
        try {
            if (finalized) return false;
            write.run();
            return true;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /** Key under which FlowInfoStorage tracks this upload: identifier plus staging path. */
    public String storageKey() {
        return FlowInfoStorage.keyFor(flowIdentifier, flowFilePath);
    }

    /** Whether finalization has already happened, so a late duplicate chunk can be acknowledged. */
    public boolean isFinalized() {
        lifecycle.readLock().lock();
        try {
            return finalized;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /**
     * Whether another request wants the same final file. Two requests can share a staging key yet
     * want different final names -- e.g. through an existing symlinked .temp that canonicalises
     * onto another upload's staging file -- and must not silently inherit each other's destination.
     */
    public boolean sameDestination(FlowInfo other) {
        return (other != null) && (finalFilePath != null) &&
                   finalFilePath.equals(other.finalFilePath);
    }

    /** Whether another request declared the same chunk size and total size as this upload. */
    public boolean sameGeometry(FlowInfo other) {
        return (other != null) && (other.flowChunkSize == flowChunkSize) &&
                   (other.flowTotalSize == flowTotalSize);
    }

    public boolean valid() {
        if (flowChunkSize <= 0 || flowTotalSize < 0 || HttpUtils.isEmpty(flowIdentifier) ||
            HttpUtils.isEmpty(flowFilename) || HttpUtils.isEmpty(flowRelativePath)) {
            return false;
        } else {
            return true;
        }
    }

    // Exclusive: concurrent final chunks can both see a complete set, trimming and renaming must
    // succeed at most once, and no chunk write may be in flight while they happen.
    public String checkIfUploadFinished() {
        lifecycle.writeLock().lock();
        try {
            return finalizeIfComplete();
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private String finalizeIfComplete() {
        if (finalized) return null;
        // check if upload finished
/*
   System.out.println("checkIfUploadFinished()");
   System.out.println(flowChunkSize + " / " + flowTotalSize);
   System.out.println("uploadedChunks: " + uploadedChunks);
 */
        // long, and via UploadPaths so the servlet's geometry check and this agree. `count + 1`
        // as an int overflowed to Integer.MIN_VALUE for a large count, skipping the loop entirely
        // and declaring a one-chunk upload complete.
        long count = UploadPaths.declaredChunkCount(flowChunkSize, flowTotalSize);

        for (long i = 1; i <= count; i++) {
// System.out.println(i + "?");
            if (!uploadedChunks.contains(new flowChunkNumber((int)i))) {
// System.out.println("failed on i=" + i);
                return null;
            }
        }
        // Upload finished, move the staging file to its validated destination.
        File file = new File(flowFilePath);
        if (finalFilePath == null) {
            System.out.println("WARNING: FlowInfo has no validated destination for " + flowFilePath);
            return null;
        }
        String new_path = finalFilePath;
        // Staging files are opened without truncation, so a shorter upload landing on a .temp left
        // by an abandoned longer one would otherwise keep the old file's trailing bytes. Every
        // declared chunk has been written by now, so anything past the declared size is stale.
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            // every chunk being marked is not proof the bytes are all there; a short file would
            // hand a truncated image to the importer
            if (size < flowTotalSize) {
                System.out.println("WARNING: FlowInfo staging file " + flowFilePath + " is " + size +
                    " bytes, declared " + flowTotalSize + "; not finalizing");
                return null;
            }
            if (size > flowTotalSize) channel.truncate(flowTotalSize);
        } catch (IOException ex) {
            System.out.println("WARNING: FlowInfo could not trim " + flowFilePath + ": " + ex);
            return null;
        }
        if (!file.renameTo(new File(new_path))) {
            // an ignored rename left a .temp file behind while still reporting completion
            System.out.println("WARNING: FlowInfo could not finalize " + flowFilePath);
            return null;
        }
        finalized = true;
        return new_path;
    }
}
