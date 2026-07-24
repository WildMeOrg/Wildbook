package org.ecocean.api.patch;

import javax.jdo.PersistenceManager;

import org.ecocean.api.ApiException;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EncounterPatchValidatorTest {
    Shepherd mockShepherd;
    PersistenceManager mockPM;
    Encounter mockEnc;
    User mockUser;
    java.util.HashMap<Integer, String> savedNamesCache;

    @SuppressWarnings("unchecked")
    private java.util.HashMap<Integer, String> namesCache()
    throws Exception {
        java.lang.reflect.Field f = MarkedIndividual.class.getDeclaredField("NAMES_CACHE");

        f.setAccessible(true);
        return (java.util.HashMap<Integer, String>)f.get(null);
    }

    @BeforeEach void setUp()
    throws Exception {
        mockShepherd = mock(Shepherd.class);
        mockPM = mock(PersistenceManager.class);
        mockEnc = mock(Encounter.class);
        mockUser = mock(User.class);
        when(mockShepherd.getPM()).thenReturn(mockPM);
        when(mockEnc.getGenus()).thenReturn("Carcharodon");
        when(mockEnc.getSpecificEpithet()).thenReturn("carcharias");
        // real addName() during the create path touches the global names cache
        savedNamesCache = new java.util.HashMap<Integer, String>(namesCache());
    }

    @org.junit.jupiter.api.AfterEach void tearDown()
    throws Exception {
        namesCache().clear();
        namesCache().putAll(savedNamesCache);
    }

    private JSONObject individualIdPatch(String value) {
        JSONObject patch = new JSONObject();

        patch.put("op", "replace");
        patch.put("path", "individualId");
        patch.put("value", value);
        return patch;
    }

    @Test void typedNameAttachesExistingIndividual()
    throws ApiException {
        MarkedIndividual existing = mock(MarkedIndividual.class);

        when(existing.getId()).thenReturn("11111111-2222-3333-4444-555555555555");
        // no PK match for the human-readable name
        when(mockShepherd.getMarkedIndividual("GNS-2620")).thenReturn(null);
        try (MockedStatic<MarkedIndividual> mockedIndiv = mockStatic(MarkedIndividual.class, CALLS_REAL_METHODS)) {
            mockedIndiv.when(() -> MarkedIndividual.findByExactName(mockShepherd, "GNS-2620",
                "Carcharodon", "carcharias")).thenReturn(
                new ArrayList<>(Arrays.asList(existing)));
            JSONObject res = EncounterPatchValidator.applyPatch(mockEnc,
                individualIdPatch("GNS-2620"), mockUser, mockShepherd);
            // the *existing* individual must be attached...
            verify(mockEnc).applyPatchOp(eq("individualId"), same(existing), eq("replace"));
            // ...no new individual persisted...
            verify(mockPM, never()).makePersistent(any(MarkedIndividual.class));
            // ...and the resolved id is reported so clients can chain by uuid
            assertEquals("11111111-2222-3333-4444-555555555555", res.optString("individualId"));
        }
    }

    @Test void ambiguousNameThrowsConflict() {
        MarkedIndividual one = mock(MarkedIndividual.class);
        MarkedIndividual two = mock(MarkedIndividual.class);

        when(mockShepherd.getMarkedIndividual("GNS-2620")).thenReturn(null);
        try (MockedStatic<MarkedIndividual> mockedIndiv = mockStatic(MarkedIndividual.class, CALLS_REAL_METHODS)) {
            mockedIndiv.when(() -> MarkedIndividual.findByExactName(any(Shepherd.class),
                anyString(), any(), any())).thenReturn(
                new ArrayList<>(Arrays.asList(one, two)));
            assertThrows(ApiException.class, () -> {
                EncounterPatchValidator.applyPatch(mockEnc, individualIdPatch("GNS-2620"),
                    mockUser, mockShepherd);
            });
            verify(mockPM, never()).makePersistent(any(MarkedIndividual.class));
        }
    }

    @Test void unknownNameCreatesNewIndividual()
    throws ApiException {
        when(mockShepherd.getMarkedIndividual("GNS-9999")).thenReturn(null);
        try (MockedStatic<MarkedIndividual> mockedIndiv = mockStatic(MarkedIndividual.class, CALLS_REAL_METHODS)) {
            mockedIndiv.when(() -> MarkedIndividual.findByExactName(any(Shepherd.class),
                anyString(), any(), any())).thenReturn(new ArrayList<MarkedIndividual>());
            JSONObject res = EncounterPatchValidator.applyPatch(mockEnc,
                individualIdPatch("GNS-9999"), mockUser, mockShepherd);
            ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
            verify(mockPM).makePersistent(persisted.capture());
            assertTrue(persisted.getValue() instanceof MarkedIndividual);
            MarkedIndividual created = (MarkedIndividual)persisted.getValue();
            assertTrue(created.hasName("GNS-9999"));
            assertNotNull(res.optString("individualId", null));
        }
    }

    @Test void semicolonNameRejected() {
        when(mockShepherd.getMarkedIndividual("GNS;2620")).thenReturn(null);
        try (MockedStatic<MarkedIndividual> mockedIndiv = mockStatic(MarkedIndividual.class,
            CALLS_REAL_METHODS)) {
            assertThrows(ApiException.class, () -> {
                EncounterPatchValidator.applyPatch(mockEnc, individualIdPatch("GNS;2620"),
                    mockUser, mockShepherd);
            });
            mockedIndiv.verify(() -> MarkedIndividual.findByExactName(any(Shepherd.class),
                anyString(), any(), any()), never());
            verify(mockPM, never()).makePersistent(any(MarkedIndividual.class));
        }
    }

    @Test void pkMatchSkipsNameLookup()
    throws ApiException {
        MarkedIndividual existing = mock(MarkedIndividual.class);

        when(existing.getId()).thenReturn("aaaa1111-2222-3333-4444-555555555555");
        when(mockShepherd.getMarkedIndividual("aaaa1111-2222-3333-4444-555555555555")).thenReturn(
            existing);
        try (MockedStatic<MarkedIndividual> mockedIndiv = mockStatic(MarkedIndividual.class, CALLS_REAL_METHODS)) {
            EncounterPatchValidator.applyPatch(mockEnc,
                individualIdPatch("aaaa1111-2222-3333-4444-555555555555"), mockUser, mockShepherd);
            mockedIndiv.verify(() -> MarkedIndividual.findByExactName(any(Shepherd.class),
                anyString(), any(), any()), never());
            verify(mockEnc).applyPatchOp(eq("individualId"), same(existing), eq("replace"));
            verify(mockPM, never()).makePersistent(any(MarkedIndividual.class));
        }
    }
}
