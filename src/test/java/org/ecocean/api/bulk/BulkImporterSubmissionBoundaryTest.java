package org.ecocean.api.bulk;

import java.util.*;
import org.ecocean.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BulkImporterSubmissionBoundaryTest {
    @Test void deferredImportCapturesActualRowsWithoutDispatchAndLegacyStillDispatches() throws Exception {
        Shepherd sh = mock(Shepherd.class); when(sh.getContext()).thenReturn("context0");
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getOrCreateOccurrence(null)).thenAnswer(inv -> new Occurrence(Util.generateUUID()));
        when(sh.getUser(anyString())).thenReturn(mock(User.class));
        when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        JSONObject row = new JSONObject().put("Encounter.year", 2026).put("Encounter.genus", "Manta")
            .put("Encounter.specificEpithet", "birostris").put("Encounter.submitterID", "owner");
        List<Map<String, Object>> rows = Arrays.asList(BulkImportUtil.validateRow(row, sh), BulkImportUtil.validateRow(new JSONObject(row.toString()), sh));
        Map<Integer, Encounter> resolved = new HashMap<>();
        try (MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class)) {
            BulkImporter importer = new BulkImporter("reserved-task", rows, null, null, sh).deferSideEffects(resolved::put);
            JSONObject output = importer.createImport();
            media.verifyNoInteractions();
            verify(sh, never()).storeNewEncounter(any(), any());
            verify(sh, never()).storeNewOccurrence(any());
            verify(pm, atLeastOnce()).makePersistent(any(Encounter.class));
            assertEquals(2, resolved.size());
            assertNotEquals(resolved.get(0).getId(), resolved.get(1).getId());
            assertEquals(2, output.getJSONArray("encounters").length());
            assertEquals(2026, resolved.get(0).getYear());
            new BulkImporter(null, rows, null, null, sh).createImport();
            verify(sh, atLeastOnce()).storeNewEncounter(any(), any());
            media.verify(() -> MediaAsset.updateStandardChildrenBackground(eq("context0"), anyList(), any(Runnable.class)));
        }
        verify(sh, never()).commitDBTransaction();
        verify(sh, never()).commitDBTransactionWithStatus();
    }
}
