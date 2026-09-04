package org.ecocean;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Durable storage for a stored OpenSearch query (see SearchApi "searchQueryId").
 * The value column holds the merged JSON previously written to a /tmp file:
 * the original query body plus id, indexName, created, creator.
 */
public class OpenSearchQuery implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String value;
    private long created;

    public OpenSearchQuery() {}

    public OpenSearchQuery(String id, JSONObject value, long created) {
        this.id = id;
        if (value != null) this.value = value.toString();
        this.created = created;
    }

    public String getId() { return id; }

    public long getCreated() { return created; }

    public JSONObject getValue() {
        return Util.stringToJSONObject(value);
    }

    public static OpenSearchQuery load(Shepherd myShepherd, String id) {
        if (id == null) return null;
        try {
            return ((OpenSearchQuery)(myShepherd.getPM().getObjectById(
                       myShepherd.getPM().newObjectIdInstance(OpenSearchQuery.class, id), true)));
        } catch (Exception ex) {
            return null;
        }
    }
}
