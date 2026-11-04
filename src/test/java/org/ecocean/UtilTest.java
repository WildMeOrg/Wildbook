package org.ecocean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Test void testDateFuture() {
        LocalDate today = LocalDate.of(2026, 9, 25);

        assertFalse(Util.dateIsInFuture(null, null, null, today));
        assertFalse(Util.dateIsInFuture(2025, null, null, today));
        assertFalse(Util.dateIsInFuture(2026, null, null, today));
        assertFalse(Util.dateIsInFuture(2026, 9, null, today));
        assertFalse(Util.dateIsInFuture(2026, 9, 25, today));
        assertFalse(Util.dateIsInFuture(2026, 8, 31, today));
        assertTrue(Util.dateIsInFuture(2026, 10, null, today));
        assertTrue(Util.dateIsInFuture(2026, 9, 26, today));
        assertTrue(Util.dateIsInFuture(2027, null, null, today));
        assertTrue(Util.dateIsInFuture(2027, 1, 1, today));
        // year boundary: the next calendar year is future only once it has begun
        LocalDate newYearsEve = LocalDate.of(2026, 12, 31);
        assertFalse(Util.dateIsInFuture(2026, 12, 31, newYearsEve));
        assertTrue(Util.dateIsInFuture(2027, 1, 1, newYearsEve));
    }

    // at 19:00 UTC a UTC server is still on the 25th while Sydney (UTC+10) is already on the 26th;
    // the observer's "today" must not be rejected, but a date beyond UTC+14's today still is
    @Test void testDateFutureAllowsSubmittersAheadOfServer() {
        Instant now = Instant.parse("2026-09-25T19:00:00Z");
        LocalDate latestToday = now.atOffset(Util.LATEST_CIVIL_OFFSET).toLocalDate();
        LocalDate serverToday = now.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate sydneyToday = now.atOffset(ZoneOffset.ofHours(10)).toLocalDate();

        assertEquals(LocalDate.of(2026, 9, 25), serverToday);
        assertEquals(LocalDate.of(2026, 9, 26), sydneyToday);
        assertFalse(Util.dateIsInFuture(2026, 9, 26, latestToday));
        assertFalse(Util.dateIsInFuture(2026, 9, 25, latestToday));
        assertTrue(Util.dateIsInFuture(2026, 9, 27, latestToday));
        // the clock-based entry point accepts the current date at UTC+14 (a later read can only
        // move "today" forward, so this cannot flake at midnight)
        LocalDate latestNow = LocalDate.now(Util.LATEST_CIVIL_OFFSET);
        assertFalse(Util.dateIsInFuture(latestNow.getYear(), latestNow.getMonthValue(),
            latestNow.getDayOfMonth()));
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
