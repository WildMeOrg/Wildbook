package org.ecocean;

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
}
