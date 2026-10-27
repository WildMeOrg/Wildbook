package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #1733: match-results images carry the encounter date and image filename for everyone, and
 * the encounter's location ID only for viewers allowed to see that encounter.
 */
class MatchResultCaptionFieldsTest {
    private final Map<String, Encounter> encByAnnId = new HashMap<String, Encounter>();
    private int annCounter = 0;
    private Shepherd shepherd;
    private User viewer;

    @BeforeEach void reset() {
        encByAnnId.clear();
        annCounter = 0;
        shepherd = mock(Shepherd.class);
        lenient().when(shepherd.getEncountersByAnnotationIds(any())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            Map<String, Encounter> out = new HashMap<String, Encounter>();
            for (String id : ids) {
                if (encByAnnId.containsKey(id)) out.put(id, encByAnnId.get(id));
            }
            return out;
        });
        viewer = mock(User.class);
    }

    // not public, not owned, canUserView false unless a test says otherwise
    private Encounter mockEnc(String owner, String locationId) {
        Encounter enc = mock(Encounter.class);
        lenient().when(enc.getId()).thenReturn("enc-" + (++annCounter));
        lenient().when(enc.getAssignedUsername()).thenReturn(owner);
        lenient().when(enc.getLocationID()).thenReturn(locationId);
        lenient().when(enc.getDate()).thenReturn("2019-04-22 10:31");
        return enc;
    }

    private Annotation mockAnn(Encounter enc, String userFilename) {
        Annotation ann = mock(Annotation.class);
        String id = "ann-" + (++annCounter);
        lenient().when(ann.getId()).thenReturn(id);
        lenient().when(ann.getOpensearchScore()).thenReturn(0.5);
        lenient().when(ann.findEncounter(any())).thenReturn(enc);
        MediaAsset ma = mock(MediaAsset.class);
        lenient().when(ma.toSimpleJSONObject(any())).thenReturn(new JSONObject());
        lenient().when(ma.getUserFilename()).thenReturn(userFilename);
        lenient().when(ann.getMediaAsset()).thenReturn(ma);
        encByAnnId.put(id, enc);
        return ann;
    }

    private JSONObject details(Encounter enc, MatchResultViewer mrv) {
        return MatchResult.annotationDetails(mockAnn(enc, "IMG_1.JPG"), shepherd, null, mrv);
    }

    private boolean hasLocation(JSONObject aj) {
        return aj.getJSONObject("encounter").has("locationId");
    }

    @Test void filenameIsLastPathSegment() {
        Encounter enc = mockEnc("owner", "loc");
        String[][] cases = {
            { "IMG_1522.jpeg", "IMG_1522.jpeg" },
            { "/data/wildbook_data_dir/a/b/DSC_4471.JPG", "DSC_4471.JPG" },
            { "C:\\Users\\field\\GoPro_0193.JPG", "GoPro_0193.JPG" },
            { "mixed/dir\\name.png", "name.png" },
        };
        for (String[] c : cases) {
            JSONObject aj = MatchResult.annotationDetails(mockAnn(enc, c[0]), shepherd, null, null);
            assertEquals(c[1], aj.getJSONObject("asset").optString("filename", null), c[0]);
        }
    }

    @Test void blankOrMissingFilenameIsOmitted() {
        Encounter enc = mockEnc("owner", "loc");
        for (String fn : new String[] { null, "", "  ", "some/dir/", "dir\\" }) {
            JSONObject aj = MatchResult.annotationDetails(mockAnn(enc, fn), shepherd, null, null);
            assertFalse(aj.getJSONObject("asset").has("filename"), "filename for " + fn);
        }
    }

    @Test void encounterDateIncludedUnlessUnknown() {
        Encounter enc = mockEnc("owner", "loc");
        assertEquals("2019-04-22 10:31",
            details(enc, null).getJSONObject("encounter").optString("date", null));
        when(enc.getDate()).thenReturn("2019");
        assertEquals("2019", details(enc, null).getJSONObject("encounter").optString("date", null));
        when(enc.getDate()).thenReturn("Unknown");
        assertFalse(details(enc, null).getJSONObject("encounter").has("date"));
    }

    @Test void nullViewerNeverSeesLocation() {
        Encounter enc = mockEnc("owner", "loc");
        lenient().when(enc.isPubliclyReadable()).thenReturn(true);
        assertFalse(hasLocation(details(enc, null)));
        assertFalse(hasLocation(details(enc, new MatchResultViewer(null, shepherd))));
    }

    @Test void publicOwnedOrPermittedEncounterShowsLocation() {
        Encounter pub = mockEnc("public", "loc-pub");
        when(pub.isPubliclyReadable()).thenReturn(true);
        Encounter owned = mockEnc("me", "loc-own");
        when(owned.isUserOwner(viewer)).thenReturn(true);
        Encounter shared = mockEnc("colleague", "loc-shared");
        when(shared.canUserView(viewer, shepherd)).thenReturn(true);

        MatchResultViewer mrv = new MatchResultViewer(viewer, shepherd);
        assertEquals("loc-pub", details(pub, mrv).getJSONObject("encounter").getString("locationId"));
        assertEquals("loc-own", details(owned, mrv).getJSONObject("encounter").getString("locationId"));
        assertEquals("loc-shared",
            details(shared, mrv).getJSONObject("encounter").getString("locationId"));
        // public and owned never need the expensive check
        verify(pub, never()).canUserView(any(), any());
        verify(owned, never()).canUserView(any(), any());
    }

    @Test void deniedEncounterOmitsLocationButKeepsDateAndFilename() {
        Encounter enc = mockEnc("stranger", "loc-secret");
        JSONObject aj = details(enc, new MatchResultViewer(viewer, shepherd));
        assertFalse(hasLocation(aj));
        assertFalse(aj.toString().contains("loc-secret"));
        assertEquals("2019-04-22 10:31", aj.getJSONObject("encounter").getString("date"));
        assertEquals("IMG_1.JPG", aj.getJSONObject("asset").getString("filename"));
    }

    @Test void accessCheckIsCachedPerOwnerAndLocation() {
        Encounter first = mockEnc("stranger", "loc");
        Encounter second = mockEnc("stranger", "loc");
        MatchResultViewer mrv = new MatchResultViewer(viewer, shepherd);
        assertFalse(hasLocation(details(first, mrv)));
        assertFalse(hasLocation(details(second, mrv)));
        verify(first, times(1)).canUserView(viewer, shepherd);
        verify(second, never()).canUserView(any(), any());
    }

    @Test void ownershipIsCheckedPerEncounterNotCached() {
        // same cache key; the viewer is a co-submitter of the second encounter only
        Encounter notMine = mockEnc("stranger", "loc");
        Encounter mine = mockEnc("stranger", "loc");
        when(mine.isUserOwner(viewer)).thenReturn(true);
        MatchResultViewer mrv = new MatchResultViewer(viewer, shepherd);
        assertFalse(hasLocation(details(notMine, mrv)));
        assertTrue(hasLocation(details(mine, mrv)));
    }

    @Test void nullLocationAndLiteralNullStringAreDifferentKeys() {
        Encounter noLoc = mockEnc("stranger", null);
        Encounter nullNamed = mockEnc("stranger", "null");
        when(nullNamed.canUserView(viewer, shepherd)).thenReturn(true);
        MatchResultViewer mrv = new MatchResultViewer(viewer, shepherd);
        // deny the null location first, then the literal "null" location must be checked afresh
        assertFalse(hasLocation(details(noLoc, mrv)));
        assertTrue(hasLocation(details(nullNamed, mrv)));
        // and the reverse order
        mrv = new MatchResultViewer(viewer, shepherd);
        assertTrue(hasLocation(details(nullNamed, mrv)));
        assertFalse(hasLocation(details(noLoc, mrv)));
    }

    @Test void viewerReachesQueryAndEveryProspect() throws Exception {
        Encounter queryEnc = mockEnc("me", "loc-query");
        when(queryEnc.isUserOwner(viewer)).thenReturn(true);
        Encounter allowed = mockEnc("colleague", "loc-ok");
        when(allowed.canUserView(viewer, shepherd)).thenReturn(true);
        Encounter denied = mockEnc("stranger", "loc-no");

        List<Annotation> cands = new ArrayList<Annotation>();
        cands.add(mockAnn(allowed, "a.jpg"));
        cands.add(mockAnn(denied, "b.jpg"));
        Task task = mock(Task.class);
        when(task.countObjectAnnotations()).thenReturn(1);
        ArrayList<Annotation> queryList = new ArrayList<Annotation>();
        queryList.add(mockAnn(queryEnc, "q.jpg"));
        when(task.getObjectAnnotations()).thenReturn(queryList);
        MatchResult mr = new MatchResult(task, cands, cands.size(), shepherd);

        JSONObject json = mr.jsonForApiGet(-1, null, shepherd,
            new MatchResultViewer(viewer, shepherd));
        assertEquals("loc-query",
            json.getJSONObject("queryAnnotation").getJSONObject("encounter").getString("locationId"));
        int seen = 0;
        for (String type : json.getJSONObject("prospects").keySet()) {
            JSONArray arr = json.getJSONObject("prospects").getJSONArray(type);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject enc = arr.getJSONObject(i).getJSONObject("annotation").getJSONObject(
                    "encounter");
                String fn = arr.getJSONObject(i).getJSONObject("annotation").getJSONObject(
                    "asset").getString("filename");
                if ("a.jpg".equals(fn)) assertEquals("loc-ok", enc.getString("locationId"));
                if ("b.jpg".equals(fn)) assertFalse(enc.has("locationId"));
                seen++;
            }
        }
        assertTrue(seen >= 2, "both prospects serialized");
        assertFalse(json.toString().contains("loc-no"));
    }
}
