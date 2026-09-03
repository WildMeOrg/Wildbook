package org.ecocean;

import java.util.Calendar;
import java.util.List;
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

    @Test void testHumanApprox() {
        Long ms = 1003L;
        assertEquals("1 second", Util.millisToHumanApprox(ms));
        ms = 21100L;
        assertEquals("21 seconds", Util.millisToHumanApprox(ms));
        ms = 120333L;
        assertEquals("2 minutes", Util.millisToHumanApprox(ms));
        ms = 11L * 60L * 60L * 1000L;
        assertEquals("11 hours", Util.millisToHumanApprox(ms));
        ms = 191L * 24L * 60L * 60L * 1000L;
        assertEquals("191 days", Util.millisToHumanApprox(ms));
    }

    // some of these assertions may fail if the world collapses
    // into political chaos
    @Test void testCountries() {
        List<String> cs = Util.getCountries();

        assertNotNull(cs);
        assertTrue(cs.size() > 100);
        assertTrue(cs.contains("Palestinian Territories"));
        assertTrue(cs.contains("United States"));
    }

    // issue: submit.jsp -> /EncounterForm silently dropped the subspecies of a
    // trinomial genusSpecies value (e.g. "Delphinus capensis tropicalis"), because it
    // only ever read two space-delimited tokens. Everything after the genus belongs to
    // the specific epithet -- that is how Encounter/Taxonomy already store trinomials.
    @Test void testParseGenusSpecies() {
        assertArrayEquals(new String[] { "Delphinus", "capensis tropicalis" },
            Util.parseGenusSpecies("Delphinus capensis tropicalis"));
        assertArrayEquals(new String[] { "Tursiops", "truncatus gephyreus" },
            Util.parseGenusSpecies("Tursiops truncatus gephyreus"));

        // plain binomials are unchanged
        assertArrayEquals(new String[] { "Megaptera", "novaeangliae" },
            Util.parseGenusSpecies("Megaptera novaeangliae"));

        // legacy normalization is preserved: commas dropped, underscores become spaces
        assertArrayEquals(new String[] { "Delphinus", "capensis tropicalis" },
            Util.parseGenusSpecies("Delphinus capensis_tropicalis"));
        assertArrayEquals(new String[] { "Megaptera", "novaeangliae" },
            Util.parseGenusSpecies("Megaptera novaeangliae,"));

        // surrounding/repeated whitespace must not leak into the epithet
        assertArrayEquals(new String[] { "Delphinus", "capensis tropicalis" },
            Util.parseGenusSpecies("  Delphinus   capensis  tropicalis "));

        // no epithet (or no value at all) -> null, so callers keep their existing
        // "malformed genusSpecies" handling
        assertNull(Util.parseGenusSpecies("Delphinus"));
        assertNull(Util.parseGenusSpecies("unknown"));
        assertNull(Util.parseGenusSpecies(""));
        assertNull(Util.parseGenusSpecies("   "));
        assertNull(Util.parseGenusSpecies(null));
    }

    // whatever the legacy two-token parse accepted must still be accepted -- a value with
    // an epithet is never rejected here, it is judged downstream exactly as before. in
    // particular do not run the parts through Util.stringExists(), which is false for the
    // literal strings "unknown" and "none".
    @Test void testParseGenusSpeciesLegacyBoundaries() {
        assertArrayEquals(new String[] { "unknown", "species" },
            Util.parseGenusSpecies("unknown species"));
        assertArrayEquals(new String[] { "Delphinus", "unknown" },
            Util.parseGenusSpecies("Delphinus unknown"));
        assertArrayEquals(new String[] { "Delphinus", "none" },
            Util.parseGenusSpecies("Delphinus none"));

        // an underscore-only epithet normalizes to whitespace, which the old code also
        // produced; Util.taxonomyString() then reports it as a genus-only taxonomy
        assertArrayEquals(new String[] { "Delphinus", " " },
            Util.parseGenusSpecies("Delphinus _"));
        assertEquals("Delphinus", Util.taxonomyString("Delphinus", " "));

        // commas are dropped from the epithet only -- a comma left on the genus is a
        // config typo, and it survived into the taxonomy before this change too
        assertArrayEquals(new String[] { "Delphinus,", "capensis" },
            Util.parseGenusSpecies("Delphinus, capensis"));
    }

    // the one intentional difference from the old two-token parse: whitespace is
    // normalized before the split. the old tokenizer split on the literal space alone,
    // which had two consequences worth being rid of -- and both are deliberate here.
    @Test void testParseGenusSpeciesNormalizesWhitespace() {
        // (1) a value separated by anything other than a space parsed as a single token
        // and was rejected as malformed, silently costing the encounter its taxonomy
        assertArrayEquals(new String[] { "Delphinus", "capensis tropicalis" },
            Util.parseGenusSpecies("Delphinus\tcapensis\ttropicalis"));
        assertArrayEquals(new String[] { "Megaptera", "novaeangliae" },
            Util.parseGenusSpecies("Megaptera\nnovaeangliae"));

        // (2) stray whitespace adjacent to a real space delimiter used to be stored as
        // part of the genus or the epithet
        assertArrayEquals(new String[] { "Delphinus", "capensis" },
            Util.parseGenusSpecies("Delphinus\t capensis"));
        assertArrayEquals(new String[] { "Delphinus", "capensis" },
            Util.parseGenusSpecies("Delphinus capensis\n"));

        // trim() strips any leading/trailing character <= U+0020, so a stray control
        // character goes with the whitespace instead of into the genus
        assertArrayEquals(new String[] { "Delphinus", "capensis" },
            Util.parseGenusSpecies("\u0000Delphinus capensis"));
    }
}
