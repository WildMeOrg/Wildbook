package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

/**
 * Pure-function coverage of {@link WildbookIAM#mediaAssetToUri(MediaAsset)}
 * after the C2 promotion to {@code public static String} and
 * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
 */
class WildbookIAMMediaAssetToUriTest {

    @Test void returnsNull_whenMediaAssetIsNull() {
        assertNull(WildbookIAM.mediaAssetToUri(null));
    }

    @Test void returnsNull_whenWebUrlIsNull() {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(null);
        assertNull(WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void returnsUrl_unchanged_whenNoQuestionMark()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/images/abc.jpg"));
        assertEquals("https://example.com/images/abc.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void escapesSingleQuestionMark_to_percent_3F()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b.jpg"));
        assertEquals("https://example.com/a%3Fb.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }

    @Test void escapesEveryQuestionMark_whenMultiplePresent()
    throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.webURL()).thenReturn(new URL("https://example.com/a?b?c.jpg"));
        assertEquals("https://example.com/a%3Fb%3Fc.jpg",
            WildbookIAM.mediaAssetToUri(ma));
    }
}
