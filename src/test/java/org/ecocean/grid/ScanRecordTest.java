package org.ecocean.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the field semantics of {@link ScanRecord}, the per-run metrics row written by
 * GrothScanRunnable and counted by MetricsBot (wildbook_tasks_modifiedGroth,
 * wildbook_tasks_i3s, and the identification_tasks_completed_last24 aggregate).
 */
class ScanRecordTest {
    @Test
    void constructorMapsAllFields() {
        ScanRecord r = new ScanRecord("uuid-1", "scanR123", "123", true,
            1000L, 2000L, true, false);
        assertEquals("uuid-1", r.getId());
        assertEquals("scanR123", r.getTaskID());
        assertEquals("123", r.getEncounterNumber());
        assertTrue(r.isRightSide(), "rightSide should map from constructor");
        assertEquals(1000L, r.getStartTime());
        assertEquals(2000L, r.getEndTime());
        assertTrue(r.isGrothSuccess(), "grothSuccess should map from constructor");
        assertFalse(r.isI3sSuccess(), "i3sSuccess should map from constructor");
    }

    @Test
    void successFlagsAreIndependent() {
        ScanRecord grothOnly = new ScanRecord("a", "scanL1", "1", false, 1L, 2L, true, false);
        ScanRecord i3sOnly = new ScanRecord("b", "scanL1", "1", false, 1L, 2L, false, true);
        ScanRecord failed = new ScanRecord("c", "scanL1", "1", false, 1L, 2L, false, false);
        assertTrue(grothOnly.isGrothSuccess() && !grothOnly.isI3sSuccess(),
            "groth-only run must not credit I3S");
        assertTrue(!i3sOnly.isGrothSuccess() && i3sOnly.isI3sSuccess(),
            "i3s-only run must not credit Groth");
        assertFalse(failed.isGrothSuccess() || failed.isI3sSuccess(),
            "failed run credits neither algorithm");
    }

    @Test
    void jdoEnhancerNoArgConstructorExists() {
        // The JDO enhancer requires a no-arg constructor; deleting it breaks
        // persistence at runtime only, so pin it here at compile/test time.
        assertNotNull(new ScanRecord());
    }
}
