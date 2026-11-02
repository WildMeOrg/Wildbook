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

class BulkImporterSightingCoordinatesTest {
    // Encounter.id must be a UUID, otherwise validation drops it and the row gets a new encounter
    private static final String REUSED = "3f6a1c2e-0b7d-4e5f-9a8b-1c2d3e4f5a6b", REPEATED = "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e";
    private static Encounter located(String id, double lat, double lon) {
        Encounter enc = new Encounter(); enc.setId(id); enc.setDecimalLatitude(lat); enc.setDecimalLongitude(lon);
        return enc;
    }
    private static JSONObject row(String sighting, Double lat, Double lon) {
        JSONObject row = new JSONObject().put("Encounter.year", 2026).put("Encounter.genus", "Manta")
            .put("Encounter.specificEpithet", "birostris").put("Encounter.submitterID", "owner");
        if (sighting != null) row.put("Encounter.sightingID", sighting);
        if (lat != null) row.put("Encounter.decimalLatitude", lat).put("Encounter.decimalLongitude", lon);
        return row;
    }
    /** Coordinates each sighting has at the moment it is handed to persistence, keyed by sighting ID. */
    private static Map<String, List<Double>> importRows(boolean deferred, JSONObject... rows) throws Exception {
        Shepherd sh = mock(Shepherd.class); when(sh.getContext()).thenReturn("context0");
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class); when(sh.getPM()).thenReturn(pm);
        when(sh.getUser(anyString())).thenReturn(mock(User.class));
        when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        Occurrence withCoords = new Occurrence("existing-coords"); withCoords.setDecimalLatitude(1.0); withCoords.setDecimalLongitude(2.0);
        Occurrence withEncounter = new Occurrence("existing-encounter"); withEncounter.addEncounter(located("old", 16.0, 26.0));
        Map<String, Occurrence> existing = Map.of("existing-coords", withCoords, "existing-encounter", withEncounter);
        when(sh.getOrCreateOccurrence(any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if (id == null) return new Occurrence(Util.generateUUID());
            return existing.containsKey(id) ? existing.get(id) : new Occurrence(id);
        });
        when(sh.getEncounter(REUSED)).thenReturn(located(REUSED, 30.0, 40.0));
        Map<String, List<Double>> persisted = new HashMap<>();
        org.mockito.stubbing.Answer<Object> record = inv -> {
            Object obj = inv.getArgument(0);
            if (obj instanceof Occurrence) {
                Occurrence occ = (Occurrence)obj;
                persisted.put(occ.getId(), Arrays.asList(occ.getDecimalLatitude(), occ.getDecimalLongitude()));
            }
            return obj;
        };
        doAnswer(record).when(sh).storeNewOccurrence(any());
        doAnswer(record).when(pm).makePersistent(any());
        List<Map<String, Object>> validated = new ArrayList<>();
        for (JSONObject row : rows) validated.add(BulkImportUtil.validateRow(row, sh));
        try (MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class)) {
            BulkImporter importer = new BulkImporter(deferred ? "reserved-task" : null, validated, null, null, sh);
            if (deferred) importer.deferSideEffects((index, encounter) -> {});
            importer.createImport();
        }
        return persisted;
    }

    @Test void everyRowOfASightingCountsAndExistingCoordinatesAreKept() throws Exception {
        JSONObject unnamed = row(null, 18.0, 28.0);
        JSONObject reused = row("reuse", null, null).put("Encounter.id", REUSED); // only the loaded encounter has coordinates
        Map<String, List<Double>> got = importRows(false,
            row("solo", 10.0, 20.0),
            row("last-only", null, null), row("last-only", 11.0, 21.0),
            row("pair", 12.0, 22.0), row("pair", 13.0, 23.0),
            row("none", null, null), row("none", null, null),
            row("minus", -1.0, 24.0), row("minus", 14.0, 24.0),
            row("minus-lon", 25.0, -1.0), row("minus-lon", 26.0, 36.0),
            row("repeat", 50.0, 60.0).put("Encounter.id", REPEATED), row("repeat", 51.0, 61.0).put("Encounter.id", REPEATED),
            row("existing-coords", 15.0, 25.0),
            row("existing-encounter", 17.0, 27.0),
            unnamed, reused);
        assertEquals(Arrays.asList(10.0, 20.0), got.get("solo"), "a one-row sighting gets its row's coordinates");
        assertEquals(Arrays.asList(11.0, 21.0), got.get("last-only"), "the last row is considered");
        assertEquals(Arrays.asList(12.0, 22.0), got.get("pair"), "the first row with coordinates wins");
        assertEquals(Arrays.asList(null, null), got.get("none"));
        assertEquals(Arrays.asList(14.0, 24.0), got.get("minus"), "-1.0 latitude is still skipped as the legacy unset marker");
        assertEquals(Arrays.asList(26.0, 36.0), got.get("minus-lon"), "-1.0 longitude is still skipped as the legacy unset marker");
        assertEquals(Arrays.asList(51.0, 61.0), got.get("repeat"), "an encounter repeated across rows contributes its last row's values");
        assertEquals(Arrays.asList(1.0, 2.0), got.get("existing-coords"), "existing sighting coordinates are never overwritten");
        assertEquals(Arrays.asList(16.0, 26.0), got.get("existing-encounter"), "an existing encounter comes before imported rows");
        assertEquals(Arrays.asList(30.0, 40.0), got.get("reuse"), "an existing encounter loaded by ID contributes its coordinates");
        List<List<Double>> unnamedSightings = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : got.entrySet()) if (Util.isUUID(entry.getKey())) unnamedSightings.add(entry.getValue());
        assertEquals(Arrays.asList(Arrays.asList(18.0, 28.0)), unnamedSightings, "a row without a sighting ID gets its own located sighting");
    }

    @Test void deferredImportsDeriveTheSameWay() throws Exception {
        Map<String, List<Double>> got = importRows(true, row("solo", 10.0, 20.0), row("last-only", null, null), row("last-only", 11.0, 21.0));
        assertEquals(Arrays.asList(10.0, 20.0), got.get("solo"));
        assertEquals(Arrays.asList(11.0, 21.0), got.get("last-only"));
    }
}
