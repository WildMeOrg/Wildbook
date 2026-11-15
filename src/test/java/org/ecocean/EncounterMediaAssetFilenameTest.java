package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Issue #1799: the encounter search gallery shows each image's filename, which it reads from the
 * mediaAssets of the encounter search document.
 */
class EncounterMediaAssetFilenameTest {
    @Test void searchDocumentMediaAssetsCarryUserFilename() throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getUUID()).thenReturn("ma-uuid");
        when(ma.getUserFilename()).thenReturn("left_flank.jpg");
        JSONObject asset = serializeSingleAsset(ma);
        assertEquals("left_flank.jpg", asset.getString("userFilename"));
    }

    // legacy assets whose path is outside the store root make getUserFilename() throw; the
    // encounter must still index, with a null filename
    @Test void legacyAssetWhoseFilenameThrowsStillIndexes() throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getUUID()).thenReturn("ma-uuid");
        when(ma.getUserFilename()).thenThrow(
            new IllegalArgumentException("Path not under given root"));
        JSONObject asset = serializeSingleAsset(ma);
        assertTrue(asset.isNull("userFilename"));
        assertEquals("ma-uuid", asset.getString("uuid"));
        assertTrue(asset.has("annotations"));
    }

    private JSONObject serializeSingleAsset(MediaAsset ma) throws Exception {
        Shepherd sh = mock(Shepherd.class);
        ArrayList<MediaAsset> media = new ArrayList<MediaAsset>();
        media.add(ma);

        Encounter enc = spy(new Encounter());
        enc.setCatalogNumber("enc-1");
        doReturn(true).when(enc).isPubliclyReadable();
        doReturn(Collections.emptyList()).when(enc).computeViewUsers(sh);
        doReturn(media).when(enc).getMedia();

        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        enc.opensearchDocumentSerializer(jg, sh);
        jg.writeEndObject();
        jg.close();

        return new JSONObject(sw.toString()).getJSONArray("mediaAssets").getJSONObject(0);
    }
}
