package org.ecocean.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the in-memory async-scan progress facility and active-run guard added to
 * {@link GridManager} for the restored async Groth scan (GrothScanRunnable / scanEndApplet.jsp).
 */
class GridManagerScanProgressTest {

    @Test void absentTaskReportsZeroProgress() {
        GridManager gm = new GridManager();
        assertEquals(0, gm.getScanProgressComplete("scanLnope"), "absent task -> 0 complete");
        assertEquals(0, gm.getScanProgressTotal("scanLnope"), "absent task -> 0 total");
    }

    @Test void setThenGetProgress() {
        GridManager gm = new GridManager();
        gm.setScanProgress("scanLx", 7, 100);
        assertEquals(7, gm.getScanProgressComplete("scanLx"), "complete reflects last set");
        assertEquals(100, gm.getScanProgressTotal("scanLx"), "total reflects last set");
    }

    @Test void setReplacesPreviousValue() {
        GridManager gm = new GridManager();
        gm.setScanProgress("scanLx", 7, 100);
        gm.setScanProgress("scanLx", 42, 100);
        assertEquals(42, gm.getScanProgressComplete("scanLx"), "later set replaces complete");
        assertEquals(100, gm.getScanProgressTotal("scanLx"), "total unchanged");
    }

    @Test void clearProgressResetsToZero() {
        GridManager gm = new GridManager();
        gm.setScanProgress("scanLx", 50, 100);
        gm.clearScanProgress("scanLx");
        assertEquals(0, gm.getScanProgressComplete("scanLx"), "cleared -> 0 complete");
        assertEquals(0, gm.getScanProgressTotal("scanLx"), "cleared -> 0 total");
    }

    @Test void nullTaskIsSafe() {
        GridManager gm = new GridManager();
        gm.setScanProgress(null, 1, 2);      // no-op, must not throw
        gm.clearScanProgress(null);          // no-op
        gm.endScan(null, 0L);                // no-op
        assertEquals(0, gm.getScanProgressComplete(null), "null taskID -> 0");
        assertEquals(0L, gm.tryStartScan(null), "null taskID cannot claim a scan");
        assertFalse(gm.isScanOwner(null, 1L), "null taskID never owns a scan");
    }

    @Test void tryStartScanClaimsOnceThenBlocksDuplicate() {
        GridManager gm = new GridManager();
        long token = gm.tryStartScan("scanLdup");
        assertTrue(token > 0L, "first claim returns a token");
        assertEquals(0L, gm.tryStartScan("scanLdup"), "second claim blocked while active");
        assertTrue(gm.isScanOwner("scanLdup", token), "first claimant still owns the slot");
    }

    @Test void endScanByNonOwnerIsNoOp() {
        GridManager gm = new GridManager();
        long token = gm.tryStartScan("scanLown");
        assertTrue(token > 0L, "claim succeeds");
        gm.endScan("scanLown", token + 999L);          // wrong token: must NOT release
        assertTrue(gm.isScanOwner("scanLown", token), "owner still holds the slot");
        assertEquals(0L, gm.tryStartScan("scanLown"), "slot still blocked after non-owner endScan");
    }

    @Test void endScanReleasesTheSlotForOwner() {
        GridManager gm = new GridManager();
        long token = gm.tryStartScan("scanLrel");
        assertTrue(token > 0L, "first claim succeeds");
        gm.endScan("scanLrel", token);
        assertTrue(gm.tryStartScan("scanLrel") > 0L, "claim succeeds again after endScan");
    }

    @Test void everyClaimGetsADistinctToken() {
        GridManager gm = new GridManager();
        long a = gm.tryStartScan("scanLa");
        long b = gm.tryStartScan("scanLb");
        assertTrue(a > 0L && b > 0L && a != b, "distinct taskIDs get distinct tokens");
    }
}
