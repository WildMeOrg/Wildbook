package org.ecocean;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import org.json.JSONObject;

import org.ecocean.CommonConfiguration;
import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

class BaseTest {
    // meant to test replacement of individual class .equals() with Base one
    @Test void equalsTest() {
        String uuid1 = "d316cd66-f0b1-49a4-859e-b1c4cec177ae";
        String uuid2 = "e316cd66-f0b1-49a4-859e-b1c4cec177ae";
        Integer other = 1;
        // Encounter
        Encounter enc1 = new Encounter();
        Encounter enc2 = new Encounter();

        enc1.setId(uuid1);
        enc2.setId(uuid2);
        assertFalse(enc1.equals(enc2));
        assertFalse(enc1.equals(other));
        enc2.setId(uuid1);
        assertTrue(enc1.equals(enc2));
        // Annotation
        Annotation ann1 = new Annotation();
        Annotation ann2 = new Annotation();
        ann1.setId(uuid1);
        ann2.setId(uuid2);
        assertFalse(ann1.equals(ann2));
        assertFalse(ann1.equals(other));
        ann2.setId(uuid1);
        assertTrue(ann1.equals(ann2));
    }

    @Test void jsonSerializerTest()
    throws IOException {
        String[] keys = new String[] {
            "microsatelliteMarkers", "country", "mediaAssetKeywords", "projects",
                "numberAnnotations", "dynamicProperties", "geneticSex", "taxonomy", "individualId",
                "occurrenceId", "submitters", "photographers", "distinguishingScar",
                "publiclyReadable", "locationId", "mediaAssetLabeledKeywords", "id", "state",
                "behavior", "tissueSampleIds", "biologicalMeasurements", "measurements",
                "locationName", "haplotype", "verbatimEventDate", "mediaAssets", "locationGeoPoint",
                "sex", "assignedUsername", "verbatimLocality", "numberMediaAssets", "informOthers",
                "metalTags", "version", "indexTimestamp", "patterningCode", "annotationIAClasses",
                "otherCatalogNumbers", "lifeStage", "organizations", "occurrenceRemarks",
                "livingStatus", "annotationViewpoints"
        };
        Encounter enc = new Encounter();
        Shepherd myShepherd = mock(Shepherd.class);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS)) {
            try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                CommonConfiguration.class)) {
                mockCollab.when(() -> Collaboration.securityEnabled(any(String.class))).thenReturn(
                    true);
                JSONObject json = enc.opensearchDocumentAsJSONObject(myShepherd);
                for (String key : keys) {
                    assertTrue(json.has(key));
                }
            }
        }
    }
}
