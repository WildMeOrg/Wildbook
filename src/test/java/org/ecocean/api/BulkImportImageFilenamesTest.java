package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

// Issue #1795: file names shown in the bulk import task table
class BulkImportImageFilenamesTest {
    private static MediaAsset asset(String userFilename) {
        MediaAsset ma = mock(MediaAsset.class);

        when(ma.getUserFilename()).thenReturn(userFilename);
        return ma;
    }

    @Test void returnsNamesInOrder() {
        List<String> names = BulkImport.imageFilenames(Arrays.asList(asset("IMG_0001.JPG"),
            asset("fluke left é.png")));

        assertEquals(Arrays.asList("IMG_0001.JPG", "fluke left é.png"), names);
    }

    @Test void skipsMissingAndBlankNames() {
        List<String> names = BulkImport.imageFilenames(Arrays.asList(asset(null), asset(""),
            null, asset("a.jpg")));

        assertEquals(Arrays.asList("a.jpg"), names);
    }

    @Test void oneUnresolvableAssetDoesNotBreakTheList() {
        // e.g. a legacy local asset with no userFilename and a path outside the store root
        MediaAsset broken = mock(MediaAsset.class);

        when(broken.getUserFilename()).thenThrow(new IllegalArgumentException("null path"));
        List<String> names = BulkImport.imageFilenames(Arrays.asList(asset("a.jpg"), broken,
            asset("b.jpg")));

        assertEquals(Arrays.asList("a.jpg", "b.jpg"), names);
    }

    @Test void brokenAssetWhoseToStringAlsoThrowsIsStillSkipped() {
        MediaAsset broken = mock(MediaAsset.class);

        when(broken.getUserFilename()).thenThrow(new IllegalArgumentException("null path"));
        when(broken.toString()).thenThrow(new NullPointerException("bad keyword"));
        List<String> names = BulkImport.imageFilenames(Arrays.asList(broken, asset("b.jpg")));

        assertEquals(Arrays.asList("b.jpg"), names);
    }

    @Test void nullListGivesEmptyList() {
        assertTrue(BulkImport.imageFilenames(null).isEmpty(), "null input should yield no names");
    }
}
