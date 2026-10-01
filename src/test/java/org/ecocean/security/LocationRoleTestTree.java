package org.ecocean.security;

import org.ecocean.LocationID;
import org.json.JSONObject;

/**
 * Test fixture: a small locationID tree injected as the DEFAULT tree so location-role tests do
 * not depend on the bundled locationID.json. Always restore the previous entry afterwards.
 *
 * root (no id)
 *   Pakistan
 *     PAKISTAN - North
 *     PAKISTAN - South
 *   Indonesia
 *     Flores Sea
 *       Komodo
 *   Dup  -> Twin        (Twin appears under two parents: ambiguous)
 *   Other -> Twin
 *   ""   -> Orphan      (blank-id intermediate node)
 *   researcher -> Lab   (node named like a system role)
 */
final class LocationRoleTestTree {
    private LocationRoleTestTree() {}

    static JSONObject fixture() {
        return new JSONObject("{\"description\":\"test\",\"locationID\":["
            + "{\"name\":\"Pakistan\",\"id\":\"Pakistan\",\"locationID\":["
            + "  {\"name\":\"PAKISTAN - North\",\"id\":\"PAKISTAN - North\",\"locationID\":[]},"
            + "  {\"name\":\"PAKISTAN - South\",\"id\":\"PAKISTAN - South\",\"locationID\":[]}]},"
            + "{\"name\":\"Indonesia\",\"id\":\"Indonesia\",\"locationID\":["
            + "  {\"name\":\"Flores Sea\",\"id\":\"Flores Sea\",\"locationID\":["
            + "    {\"name\":\"Komodo\",\"id\":\"Komodo\",\"locationID\":[]}]}]},"
            + "{\"name\":\"Dup\",\"id\":\"Dup\",\"locationID\":[{\"name\":\"Twin\",\"id\":\"Twin\",\"locationID\":[]}]},"
            + "{\"name\":\"Other\",\"id\":\"Other\",\"locationID\":[{\"name\":\"Twin\",\"id\":\"Twin\",\"locationID\":[]}]},"
            + "{\"name\":\"blank\",\"id\":\"\",\"locationID\":[{\"name\":\"Orphan\",\"id\":\"Orphan\",\"locationID\":[]}]},"
            + "{\"name\":\"researcher\",\"id\":\"researcher\",\"locationID\":[{\"name\":\"Lab\",\"id\":\"Lab\",\"locationID\":[]}]}"
            + "]}");
    }

    /** Installs the fixture as the default tree; returns the previous default (may be null). */
    static JSONObject inject() {
        JSONObject previous = LocationID.getJSONMaps().get("default");
        LocationID.getJSONMaps().put("default", fixture());
        return previous;
    }

    static void restore(JSONObject previous) {
        if (previous == null) LocationID.getJSONMaps().remove("default");
        else LocationID.getJSONMaps().put("default", previous);
    }
}
