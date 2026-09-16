package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the new image-side fields on {@link WbiaRegisterRequest}
 * added in C5 ({@code imageUri}, {@code imageLatitude},
 * {@code imageLongitude}, {@code imageDateTimeMillis}) and the
 * backward-compat 7-arg delegating constructor that defaults all four
 * to {@code null}. (Empty-match-prospects design Track 1 C5.)
 */
class WbiaRegisterRequestImageFieldsTest {

    @Test void elevenArgConstructorAssignsAllFields() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-1", "ann-acm-1", "ma-acm-1",
            new int[] { 10, 20, 100, 200 },
            0.5d, "salamander_fire_adult", "indiv-1",
            "https://example.com/img.jpg", 12.34d, -56.78d, 1700000000000L);
        assertEquals("ann-1", dto.annotationId);
        assertEquals("ann-acm-1", dto.annotationAcmId);
        assertEquals("ma-acm-1", dto.mediaAssetAcmId);
        assertEquals(0.5d, dto.theta, 0.0);
        assertEquals("salamander_fire_adult", dto.iaClass);
        assertEquals("indiv-1", dto.individualName);
        assertEquals("https://example.com/img.jpg", dto.imageUri);
        assertEquals(12.34d, dto.imageLatitude, 0.0);
        assertEquals(-56.78d, dto.imageLongitude, 0.0);
        assertEquals(Long.valueOf(1700000000000L), dto.imageDateTimeMillis);
    }

    @Test void elevenArgConstructorAcceptsNullsForOptionalImageFields() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-2", "ann-acm-2", "ma-acm-2",
            new int[] { 0, 0, 1, 1 },
            0.0d, "iaClass", "____",
            "https://example.com/2.jpg", null, null, null);
        assertEquals("https://example.com/2.jpg", dto.imageUri);
        assertNull(dto.imageLatitude);
        assertNull(dto.imageLongitude);
        assertNull(dto.imageDateTimeMillis);
    }

    @Test void sevenArgConstructorDefaultsAllImageFieldsToNull() {
        WbiaRegisterRequest dto = new WbiaRegisterRequest(
            "ann-3", "ann-acm-3", "ma-acm-3",
            new int[] { 1, 2, 3, 4 },
            0.0d, "iaClass", "____");
        // Backward-compat path; image fields default to null so legacy
        // test fixtures don't need to know about the C5 additions.
        assertNull(dto.imageUri);
        assertNull(dto.imageLatitude);
        assertNull(dto.imageLongitude);
        assertNull(dto.imageDateTimeMillis);
        // Annotation-side fields are still populated.
        assertEquals("ann-3", dto.annotationId);
        assertEquals("ma-acm-3", dto.mediaAssetAcmId);
    }
}
