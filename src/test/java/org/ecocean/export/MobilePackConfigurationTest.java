package org.ecocean.export;

import static org.junit.jupiter.api.Assertions.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class MobilePackConfigurationTest {
    @Test void acceptsTheDocumentedProfile() {
        MobilePackConfiguration profile = new MobilePackConfiguration(MobilePackArchiveTest.profile());
        assertEquals("horse+face", profile.featureClass());
        assertEquals(100, profile.model().getLong("sizeBytes"));
    }

    @Test void rejectsStringNumbersThatTheRegistryWouldReject() {
        JSONObject stringSize = MobilePackArchiveTest.profile();
        stringSize.getJSONObject("embeddingModel").put("sizeBytes", "100");
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(stringSize));

        JSONObject stringDim = MobilePackArchiveTest.profile();
        stringDim.getJSONObject("embeddingModel").put("embeddingDim", "2152");
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(stringDim));

        JSONObject stringMean = MobilePackArchiveTest.profile();
        stringMean.getJSONObject("embeddingModel").getJSONObject("normalize")
            .put("mean", new JSONArray().put("0").put("0").put("0"));
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(stringMean));

        JSONObject fractionalSize = MobilePackArchiveTest.profile();
        fractionalSize.getJSONObject("mobileDetector").put("sizeBytes", 100.5);
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(fractionalSize));

        JSONObject stringInput = MobilePackArchiveTest.profile();
        stringInput.getJSONObject("embeddingModel").put("inputSize", new JSONArray().put("440").put("440"));
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(stringInput));

        JSONObject booleanDim = MobilePackArchiveTest.profile();
        booleanDim.getJSONObject("embeddingModel").put("embeddingDim", true);
        assertThrows(IllegalArgumentException.class, () -> new MobilePackConfiguration(booleanDim));
    }
}
