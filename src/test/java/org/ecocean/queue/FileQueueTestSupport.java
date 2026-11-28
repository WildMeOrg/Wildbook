package org.ecocean.queue;

import java.io.File;

/**
 * Test-only bridge to FileQueue's package-private base-dir seam, for tests living outside the
 * org.ecocean.queue package (e.g. servlet-layer tests that exercise queue publishes).
 */
public final class FileQueueTestSupport {
    private FileQueueTestSupport() {}

    public static void overrideBaseDir(File dir) {
        FileQueue.overrideQueueBaseDirForTesting(dir);
    }
}
