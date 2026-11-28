package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Classic JSPs write their translation bundle into an inline script as
 * {@code new Gson().toJson(props)}. That output has to stay a plain data literal inside
 * {@code <script>} and still carry every string unchanged.
 */
class PageTranslationsJsonTest {
    @Test void bundleIsScriptSafeAndRoundTrips() {
        Properties props = new Properties();
        props.setProperty("closing", "a</script><b>x</b>");
        props.setProperty("comment", "<!-- <script>");
        props.setProperty("quotes", "it's \"quoted\" & done");
        props.setProperty("separators", "line para end");
        props.setProperty("accented", "Échec de la suppression");

        String json = new Gson().toJson(props);

        assertFalse(json.contains("<"), json);
        assertFalse(json.contains(">"), json);
        assertFalse(json.contains(" "), json);
        assertFalse(json.contains(" "), json);
        Map<?, ?> back = new Gson().fromJson(json, Map.class);
        assertEquals(props.size(), back.size());
        for (String key : props.stringPropertyNames()) {
            assertEquals(props.getProperty(key), back.get(key), key);
        }
    }
}
