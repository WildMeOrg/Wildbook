package org.ecocean;

import org.ecocean.Setting;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.Assert.*;

class SettingTest {

    static String groupGood = "language";
    static String groupBad = "foo";

    @Test void createSetting() {
        Setting st = new Setting();
        assertNotNull(st);

        st = new Setting(groupGood, "available");  // no value is allowed here, even if wonky
        assertNotNull(st);

        // this will fail, as language/site *must have* a value (list)
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            Setting st2 = new Setting(groupGood, "site");
        });
        assertTrue(ex.getMessage().contains("value must be a list"));

        ex = assertThrows(IllegalArgumentException.class, () -> {
            Setting st2 = new Setting("foo", "bar");
        });
        assertEquals(ex.getMessage(), "invalid group=foo and/or id=bar");
    }
}
