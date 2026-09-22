package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The per-request upload cap. A misconfigured or absent value must fall back to the default
 * rather than leaving the multipart parser unbounded.
 */
class UploadPathsMaxBytesTest {
    private static final long MB = 1024L * 1024L;

    @Test void absentConfigurationUsesTheFallback() {
        assertEquals(64 * MB, UploadPaths.maxUploadBytes(null, 64));
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("", 64));
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("   ", 64));
    }

    @Test void configuredMegabytesAreConverted() {
        assertEquals(128 * MB, UploadPaths.maxUploadBytes("128", 64));
        assertEquals(32 * MB, UploadPaths.maxUploadBytes("  32  ", 64), "surrounding space is fine");
    }

    @Test void unparseableConfigurationUsesTheFallback() {
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("sixty-four", 64));
    }

    @Test void nonPositiveConfigurationUsesTheFallbackRatherThanDisablingTheCap() {
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("0", 64),
            "zero must not be read as unlimited");
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("-5", 64));
    }

    @Test void largeConfiguredValueDoesNotOverflow() {
        assertEquals(100000L * MB, UploadPaths.maxUploadBytes("100000", 64),
            "megabytes must be widened before multiplying");
    }

    @Test void absurdConfiguredValueFallsBackRatherThanWrappingNegative() {
        // 8796093022208 MiB overflows a long when multiplied out, yielding a negative cap, which
        // commons-fileupload reads as "no limit" -- the opposite of what was configured.
        assertEquals(64 * MB, UploadPaths.maxUploadBytes("8796093022208", 64),
            "an unusable cap must fall back, never wrap to a negative (unlimited) value");
    }
}
