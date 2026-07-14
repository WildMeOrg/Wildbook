package org.ecocean.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * v2 commit #5: MediaAsset.setDetectionStatus bumps REVISION so the OpenSearch
 * reindexer picks up detection-status changes and so the stale-job
 * reconciler in commit #12 has a real "when did this detectionStatus change"
 * timestamp.
 */
class MediaAssetDetectionStatusTest {

    /**
     * Codex CI-flakiness caveat: setRevision() uses System.currentTimeMillis(),
     * so two adjacent setDetectionStatus calls inside the same millisecond
     * would produce the same revision. Spin until the clock advances at least
     * 5 milliseconds before re-comparing.
     */
    private void waitMillis(long ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test void setDetectionStatusBumpsRevision() {
        MediaAsset ma = new MediaAsset(null, null);
        long r0 = ma.getVersion();
        waitMillis(5);
        ma.setDetectionStatus("complete-mlservice");
        long r1 = ma.getVersion();
        assertTrue("setDetectionStatus should bump revision (was " + r0 + ", now " + r1 + ")",
            r1 > r0);
        assertEquals("complete-mlservice", ma.getDetectionStatus());
    }

    @Test void setDetectionStatusBumpsRevisionAcrossMultipleCalls() {
        MediaAsset ma = new MediaAsset(null, null);
        long r0 = ma.getVersion();
        waitMillis(5);
        ma.setDetectionStatus("processing-mlservice");
        long r1 = ma.getVersion();
        waitMillis(5);
        ma.setDetectionStatus("complete-mlservice");
        long r2 = ma.getVersion();
        assertTrue("first setDetectionStatus should bump revision", r1 > r0);
        assertTrue("second setDetectionStatus should bump revision again", r2 > r1);
    }
}
