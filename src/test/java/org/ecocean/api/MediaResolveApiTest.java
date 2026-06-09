package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MediaResolveApiTest {

    @Test void scaleBbox_identityWhenSameDims() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {10, 20, 30, 40}, 100, 200, 100, 200);
        assertArrayEquals(new int[] {10, 20, 30, 40}, out, "same src/dst dims must be identity");
    }

    @Test void scaleBbox_halfScaleDownscales() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {100, 200, 300, 400}, 1000, 1000, 500, 500);
        assertArrayEquals(new int[] {50, 100, 150, 200}, out, "0.5x scale halves every component");
    }

    @Test void scaleBbox_clampsOverflowToDerivative() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {900, 0, 400, 100}, 1000, 1000, 500, 500);
        assertArrayEquals(new int[] {450, 0, 50, 50}, out, "overflow width/height clamped to derivative bounds");
    }

    @Test void scaleBbox_nullOnBadInput() {
        assertNull(MediaResolveApi.scaleBbox(null, 100, 100, 50, 50), "null src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {1, 2, 3}, 100, 100, 50, 50), "short src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 0, 100, 50, 50), "zero src dim -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 100, 100, 0, 50), "zero dst dim -> null");
    }

    @Test void scaleBbox_negativeOriginClampsCornerAndShrinksWidth() {
        // src bbox starts at x=-10; clamping the LEFT corner to 0 must shrink width to 10, not keep 20.
        int[] out = MediaResolveApi.scaleBbox(new int[] {-10, 0, 20, 20}, 100, 100, 100, 100);
        assertArrayEquals(new int[] {0, 0, 10, 20}, out, "negative origin clamps corner and shrinks width");
    }

    @Test void scaleBbox_fullyOutsideReturnsNull() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {-50, -50, 20, 20}, 100, 100, 100, 100);
        assertNull(out, "a box entirely outside the image collapses to <1px -> null (omit)");
    }

    @Test void scaleBbox_nullWhenScaledRegionVanishes() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {0, 0, 1, 1}, 10000, 10000, 5, 5);
        assertNull(out, "a region that rounds to <1px in the derivative -> null (omit)");
    }
}
