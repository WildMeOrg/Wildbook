package org.ecocean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.jdo.PersistenceManager;

import org.ecocean.api.ApiException;
import org.ecocean.shepherd.core.Shepherd;

import org.json.JSONObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class EncounterCreateFromApiTest {
    private Encounter createViaApi(JSONObject payload)
    throws ApiException {
        Shepherd mockShepherd = mock(Shepherd.class);
        PersistenceManager mockPM = mock(PersistenceManager.class);
        Occurrence mockOcc = mock(Occurrence.class);
        List<File> files = new ArrayList<File>();

        when(mockShepherd.getContext()).thenReturn("context0");
        when(mockShepherd.getPM()).thenReturn(mockPM);
        when(mockShepherd.getOrCreateOccurrence(null)).thenReturn(mockOcc);
        try (MockedStatic<LocationID> mockLoc = mockStatic(LocationID.class)) {
            mockLoc.when(() -> LocationID.isValidLocationID(any(String.class))).thenReturn(true);
            try (MockedStatic<CommonConfiguration> mockConf = mockStatic(
                    CommonConfiguration.class)) {
                return (Encounter)Encounter.createFromApi(payload, files, mockShepherd);
            }
        }
    }

    @Test void createFromApiSetsVerbatimLocality()
    throws ApiException {
        JSONObject payload = new JSONObject();

        payload.put("locationId", "test-location");
        payload.put("dateTime", "2024-03-18T12:00:00");
        payload.put("verbatimLocality", "reef off the north point");

        Encounter enc = createViaApi(payload);
        assertEquals("reef off the north point", enc.getVerbatimLocality());
        assertEquals("test-location", enc.getLocationID());
    }

    @Test void createFromApiLeavesVerbatimLocalityNullWhenAbsent()
    throws ApiException {
        JSONObject payload = new JSONObject();

        payload.put("locationId", "test-location");
        payload.put("dateTime", "2024-03-18T12:00:00");

        Encounter enc = createViaApi(payload);
        assertNull(enc.getVerbatimLocality());
    }
}
