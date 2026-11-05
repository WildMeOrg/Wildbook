package org.ecocean.api.bulk;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BulkKeywordLengthTest {

    private static String repeat(int n) {
        return "x".repeat(n);
    }

    @Test
    void keywordAtMaxLengthIsAccepted() throws Exception {
        String kw = repeat(BulkValidator.MAX_KEYWORD_LENGTH);
        assertEquals(kw, BulkValidator.validateValue("Encounter.keyword0", kw, null));
    }

    @Test
    void keywordOverMaxLengthIsRejected() {
        String kw = repeat(BulkValidator.MAX_KEYWORD_LENGTH + 1);
        assertThrows(BulkValidatorException.class,
                () -> BulkValidator.validateValue("Encounter.keyword0", kw, null));
    }

    @Test
    void mediaAssetKeywordOverMaxLengthIsRejected() {
        String kw = "ok_" + repeat(BulkValidator.MAX_KEYWORD_LENGTH + 1);
        assertThrows(BulkValidatorException.class,
                () -> BulkValidator.validateValue("Encounter.mediaAsset0.keywords", kw, null));
    }
}