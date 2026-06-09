package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.shepherd.core.Shepherd;
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

    @Test void scaleBbox_asymmetricScaleAppliedPerAxis() {
        // sx=2 on X, sy=0.5 on Y — a single-factor bug would produce wrong Y values
        int[] out = MediaResolveApi.scaleBbox(new int[] {10, 100, 50, 200}, 100, 1000, 200, 500);
        assertArrayEquals(new int[] {20, 50, 100, 100}, out, "x and y must use their own scale factor");
    }

    @Test void scaleBbox_nullWhenScaledRegionVanishes() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {0, 0, 1, 1}, 10000, 10000, 5, 5);
        assertNull(out, "a region that rounds to <1px in the derivative -> null (omit)");
    }

    private MediaAsset child(String label, boolean urlStore) {
        MediaAsset ma = mock(MediaAsset.class);
        ArrayList<String> labels = new ArrayList<>();
        labels.add(label);
        when(ma.getLabels()).thenReturn(labels);
        when(ma.hasLabel(label)).thenReturn(true);
        when(ma.getStore()).thenReturn(urlStore ? mock(URLAssetStore.class) : mock(LocalAssetStore.class));
        return ma;
    }

    private Annotation annWithSource(MediaAsset src) {
        Annotation ann = mock(Annotation.class);
        when(ann.getMediaAsset()).thenReturn(src);
        return ann;
    }

    @Test void selectSafeDerivative_prefersMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset master = child("_master", false);
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(master);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(master, out, "a _master child must be selected first");
    }

    @Test void selectSafeDerivative_fallsBackToMidWhenNoMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(mid, out, "must fall back to _mid when _master is absent (bestSafeAsset bug bypassed)");
    }

    @Test void selectSafeDerivative_nullWhenNoSafeDerivative() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "no _master/_mid -> null (omit)");
    }

    @Test void selectSafeDerivative_rejectsUrlAssetStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(URLAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "URLAssetStore source (external original) must be rejected");
    }

    @Test void selectSafeDerivative_skipsUrlStoreChildren() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset urlChild = child("_master", true); // _master label but URLAssetStore
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(urlChild);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a non-local (URLAssetStore) child must be skipped");
    }

    @Test void selectSafeDerivative_rejectsYouTubeStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(YouTubeAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "YouTubeAssetStore source (webURL is a watch page, not image bytes) must be rejected");
    }

    @Test void selectSafeDerivative_skipsMasterAlsoLabeledOriginal_fallsBackToMid() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset masterButOriginal = child("_master", false);
        when(masterButOriginal.hasLabel("_original")).thenReturn(true); // also carries _original
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(masterButOriginal);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        assertSame(mid, MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a _master child also labeled _original is skipped; falls back to _mid");
    }

    @Test void selectSafeDerivative_nullWhenChildrenExistButNoneMatchLabel() {
        // findChildrenByLabel returns an empty list (not null) when children exist but labels don't match
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(new ArrayList<>());
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(new ArrayList<>());
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "empty child list (no matching labels) -> null");
    }

    @Test void selectSafeDerivative_skipsUrlMasterThenFallsBackToLocalMid() {
        // URL-backed _master is skipped, then a local _mid is returned
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset urlMaster = child("_master", true);
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(urlMaster);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        assertSame(mid, MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a non-local _master is skipped; falls back to the local _mid");
    }
}
