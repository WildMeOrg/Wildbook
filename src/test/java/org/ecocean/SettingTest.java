package org.ecocean;

import org.ecocean.Setting;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.Assert.*;

class SettingTest {

    static String groupGood = "language";

    @Test void basicSetting() {
        Setting st = new Setting();
        assertNotNull(st);

        assertTrue(Setting.isValidGroupAndId(groupGood, "site"));
        assertFalse(Setting.isValidGroupAndId(groupGood, "FAIL"));

        st = new Setting(groupGood, "available");  // no value is allowed here, even if wonky
        assertNotNull(st);
        assertEquals(st.getGroup(), groupGood);
        assertEquals(st.getId(), "available");
        JSONObject rtn = st.getValueRaw();
        assertNull(rtn);

        JSONObject dummyData = new JSONObject("{\"abc\": 123}");
        st.setValueRaw((String)null);
        assertNull(st.getValueRaw());
        st.setValueRaw((JSONObject)null);
        assertNull(st.getValueRaw());
        st.setValueRaw(dummyData.toString());
        rtn = st.getValueRaw();
        assertEquals(rtn.getInt("abc"), 123);

        st = new Setting(groupGood, "available", "test");  // string value
        assertNotNull(st);

        JSONObject j = new JSONObject();
        st = new Setting(groupGood, "available", j);  // json value
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

    @Test void apiRelated() {
        Setting st = new Setting(groupGood, "available");
        assertEquals(st.typeFromData(new JSONArray()), "Array");
        assertEquals(st.typeFromData("test"), "String");
        assertEquals(st.typeFromData(123), "Integer");
        assertEquals(st.typeFromData(123.0D), "Double");
        assertEquals(st.typeFromData(true), "Unknown");

        JSONObject payload = new JSONObject();
        st.setValueFromPayload(payload);
        assertEquals(st.getValueRaw().getString("type"), "Unknown");
        payload.put("data", 123);
        st.setValueFromPayload(payload);
        assertEquals(st.getValueRaw().getString("type"), "Integer");
        assertEquals(st.getValueRaw().getInt("data"), 123);
    }
}
