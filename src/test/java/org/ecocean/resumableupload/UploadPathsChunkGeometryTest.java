package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Chunk geometry is entirely client-supplied. Unchecked, it yields a division by zero that spins
 * Integer.MAX_VALUE times, an unbounded seek that creates a huge sparse file, and a 32-bit
 * multiplication that silently corrupts files past ~2GiB.
 */
class UploadPathsChunkGeometryTest {
    private static final int FIVE_MIB = 5 * 1024 * 1024;

    @Test void offsetIsComputedIn64BitsBeyondTwoGigabytes() {
        // chunk 500 at 5MiB overflows a 32-bit multiply; the true offset exceeds Integer.MAX_VALUE
        long offset = UploadPaths.chunkOffset(500, FIVE_MIB);

        assertEquals(499L * FIVE_MIB, offset);
        assertTrue(offset > Integer.MAX_VALUE, "offset past 2GiB must not wrap");
    }

    @Test void firstChunkStartsAtZero() {
        assertEquals(0L, UploadPaths.chunkOffset(1, FIVE_MIB));
    }

    @Test void ordinaryGeometryIsAccepted() {
        assertTrue(UploadPaths.chunkGeometryIsValid(1, FIVE_MIB, 12L * 1024 * 1024));
        assertTrue(UploadPaths.chunkGeometryIsValid(3, FIVE_MIB, 12L * 1024 * 1024),
            "the final, partial chunk must be accepted");
    }

    @Test void zeroChunkSizeIsRefused() {
        assertFalse(UploadPaths.chunkGeometryIsValid(1, 0, 100L),
            "a zero chunk size makes the completion check loop Integer.MAX_VALUE times");
    }

    @Test void negativeValuesAreRefused() {
        assertFalse(UploadPaths.chunkGeometryIsValid(1, -1, 100L));
        assertFalse(UploadPaths.chunkGeometryIsValid(1, FIVE_MIB, -1L));
    }

    @Test void chunkNumberBelowOneIsRefused() {
        assertFalse(UploadPaths.chunkGeometryIsValid(0, FIVE_MIB, 100L));
        assertFalse(UploadPaths.chunkGeometryIsValid(-5, FIVE_MIB, 100L));
    }

    @Test void geometryImplyingMoreChunksThanAllowedIsRefused() {
        // FlowInfo.checkIfUploadFinished() does `for (int i = 1; i < count + 1; i++)`, so a count
        // of Integer.MAX_VALUE overflows to Integer.MIN_VALUE, the loop never runs, and a single
        // one-byte chunk is declared a complete upload. Bounding the count closes that.
        assertFalse(UploadPaths.chunkGeometryIsValid(1, 1, (long)Integer.MAX_VALUE),
            "a chunk count that overflows the completion check must be refused");
        assertFalse(UploadPaths.chunkGeometryIsValid(1, 1, Long.MAX_VALUE),
            "the chunk-count ceiling must not overflow either");
    }

    @Test void declaredChunkLengthMustMatchWhatWasActuallySent() {
        assertTrue(UploadPaths.chunkLengthIsValid(FIVE_MIB, FIVE_MIB, 1, 12L * 1024 * 1024));
        assertTrue(UploadPaths.chunkLengthIsValid(2L * 1024 * 1024, FIVE_MIB, 3,
            12L * 1024 * 1024), "the final chunk is legitimately short");
        assertFalse(UploadPaths.chunkLengthIsValid(FIVE_MIB + 1L, FIVE_MIB, 1,
            12L * 1024 * 1024), "a chunk longer than declared must be refused");
        assertFalse(UploadPaths.chunkLengthIsValid(1L, FIVE_MIB, 1, 12L * 1024 * 1024),
            "a short non-final chunk would leave a hole in the file");
    }

    @Test void chunkNumberBeyondTheDeclaredTotalIsRefused() {
        // 12MiB at 5MiB per chunk is 3 chunks; a 4th would seek past the declared end
        assertFalse(UploadPaths.chunkGeometryIsValid(4, FIVE_MIB, 12L * 1024 * 1024),
            "an out-of-range chunk number allows an arbitrary sparse-file seek");
    }
}
