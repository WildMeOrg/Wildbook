package org.ecocean;

import org.ecocean.Setting;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.Assert.*;

class SettingTest {

    static String groupGood = "language";

    @Test void basicSetting() {
        Setting st = new Setting();
        assertNotNull(st);

        assertTrue(Setting.isValidGroupAndId(groupGood, "site"));
        assertFalse(Setting.isValidGroupAndId(groupGood, "FAIL"));
        assertFalse(Setting.isValidGroupAndId(groupGood, null));
        assertFalse(Setting.isValidGroupAndId(null, "FAIL"));

        st = new Setting(groupGood, "available");  // no value is allowed here, even if wonky
        assertNotNull(st);
        assertEquals(st.getGroup(), groupGood);
        assertEquals(st.getId(), "available");
        JSONObject rtn = st.getValueRaw();
        assertNull(rtn);
        assertEquals(st.toJSONObject().getString("group"), groupGood);
        assertTrue(st.toString().contains("group=" + groupGood));

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

        st.setValue("test");
        Object val = st.getValue();
        assertEquals(st.getValueString(), "test");

        st.setValue(123);
        assertTrue(st.getValueInteger() == 123);

        st.setValue(123.4D);
        assertTrue(st.getValueDouble() == 123.4D);

        st.setValue(dummyData);
        val = st.getValue();
        j = (JSONObject)val;
        assertEquals(j.getInt("abc"), 123);

        st.setValue(false);
        val = st.getValue();
        assertFalse((Boolean)val);

        List list = new ArrayList<Integer>();
        list.add(1);
        list.add(2);
        st.setValue(list);
        val = st.getValue();
        List res = (List)val;
        assertTrue(res.size() == 2);
        assertTrue((Integer)res.get(0) == 1);

        // this will fail, as language/site *must have* a value (list)
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            Setting st2 = new Setting(groupGood, "site");
        });
        assertTrue(ex.getMessage().contains("value must be a list"));
        ex = assertThrows(IllegalArgumentException.class, () -> {
            Setting st2 = new Setting(groupGood, "site", "bad value");
        });
        assertTrue(ex.getMessage().contains("value must be a list"));
        // now we have a real list, but empty
        List langs = new ArrayList<String>();
        ex = assertThrows(IllegalArgumentException.class, () -> {
            Setting st2 = new Setting(groupGood, "site", langs);
        });
        assertTrue(ex.getMessage().equals("value must have at least 1 value"));
        // FIXME we must test with a list *of values* but we need Shepherd/db for this :(

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

        Map<String,String[]> gi = Setting.getValidGroupsAndIds();
        assertTrue(gi.size() == 1);
    }
}
