package org.ecocean;

import java.util.Calendar;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class UtilTest {
    @Test void testRoundISO8601toMillis() {
        // should just passthru unchanged
        String testVal = "2024-10-28T16:36:56.656";

        assertEquals(testVal, Util.roundISO8601toMillis(testVal));
        testVal = "2024-10-28T16:36:56";
        assertEquals(testVal, Util.roundISO8601toMillis(testVal));
        assertNull(Util.roundISO8601toMillis(null));

        // this should round up
        testVal = "2024-10-28T16:36:56.656839";
        assertEquals("2024-10-28T16:36:56.657", Util.roundISO8601toMillis(testVal));
        // round down
        testVal = "2024-10-28T16:36:56.656039";
        assertEquals("2024-10-28T16:36:56.656", Util.roundISO8601toMillis(testVal));

        // should fall thru due to exception in parsing float
        testVal = "2024-10-28T16:36:56.1ABC";
        assertEquals(testVal, Util.roundISO8601toMillis(testVal));
    }

    // note there is an extremely slim chance that if this test is run a couple cpu
    // cycles before midnight, it might return invalid results. taking my chances.
    @Test void testDateFuture() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1; // frikken zero-based months!
        int day = cal.get(Calendar.DAY_OF_MONTH);

        assertFalse(Util.dateIsInFuture(null, null, null));
        assertFalse(Util.dateIsInFuture(year - 1, null, null));
        assertFalse(Util.dateIsInFuture(year, null, null));
        assertFalse(Util.dateIsInFuture(year, month, null));
        assertFalse(Util.dateIsInFuture(year, month, day));
        assertTrue(Util.dateIsInFuture(year, month + 1, null));
        assertTrue(Util.dateIsInFuture(year, month, day + 1));
        assertTrue(Util.dateIsInFuture(year + 1, month, day));
    }
}
