package org.ecocean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jdo.Query;
import org.ecocean.api.ApiException;
import org.ecocean.OpenSearch;
import org.json.JSONObject;

/**
 * Base class for other classes such as Encounter.java, Occurrence.java, and MarkedIndividual.java
 *
 * @author nishanth_nattoji
 */
@JsonSerialize(using = BaseSerializer.class) @JsonDeserialize(using =
    BaseDeserializer.class) public abstract class Base {

    /**
     * Retrieves Id, such as:
     *
     * <li>Catalog Number for an Encounter</li>
     * <li>Occurrence ID for an Occurrence</li>
     * <li>Individual ID for a Marked Individual</li>
     * <br>
     *
     * @return Id String
     */
    public abstract String getId();

    /**
     * Sets Id, such as:
     *
     * <li>Catalog Number for an Encounter</li>
     * <li>Occurrence ID for an Occurrence</li>
     * <li>Individual ID for a Marked Individual</li>
     * <br>
     *
     * @param id to set
     */
    public abstract void setId(final String id);

    /**
     * The computed version, such as a value computed using the 'modified' property of Encounter.java
     *
     * @return Version long value
     */
    public abstract long getVersion();

    /**
     * Retrieves the recorded comments such as for an Occurrence or a Marked Individual and occurrence remarks for an Encounter.
     *
     * @return Comments String
     */
    public abstract String getComments();

    /**
     * Sets the recorded comments such as for an Occurrence or a Marked Individual and occurrence remarks for an Encounter.
     *
     * @param comments to set
     */
    public abstract void setComments(final String comments);

    /**
     * Adds to the comments recorded for an Occurrence or a Marked Individual and to researcher comments for an Encounter.
     *
     * @param newComments to add
     */
    public abstract void addComments(final String newComments);

    public abstract List<String> userIdsWithViewAccess(Shepherd myShepherd);
    public abstract List<String> userIdsWithEditAccess(Shepherd myShepherd);

    public abstract String opensearchIndexName();

    public void opensearchCreateIndex()
    throws IOException {
        OpenSearch opensearch = new OpenSearch();

        opensearch.createIndex(opensearchIndexName(), opensearchMapping());
    }

    // this should be overridden (but use this as starting point) if any properties need special mappings (likely)
    public JSONObject opensearchMapping() {
        JSONObject map = new JSONObject();

        // we *must have* version as we likely sort on this for sync(), so even an empty index will have it in mapping
        map.put("version", new org.json.JSONObject("{\"type\": \"long\"}"));
        // id should be keyword for the sake of sorting
        map.put("id", new org.json.JSONObject("{\"type\": \"keyword\"}"));
        return map;
    }

    public void opensearchIndex()
    throws IOException {
        OpenSearch opensearch = new OpenSearch();

        opensearch.index(this.opensearchIndexName(), this);
    }

    // this will index "related" objects as needed
    // should be overridden by class-specific code
    public void opensearchIndexDeep()
    throws IOException {
        this.opensearchIndex();
    }

    public void opensearchUnindex()
    throws IOException {
        OpenSearch opensearch = new OpenSearch();

        opensearch.delete(this.opensearchIndexName(), this);
    }

    public void opensearchUnindexQuiet() {
        try {
            this.opensearchUnindex();
        } catch (IOException ex) {
            System.out.println("opensearchUnindexQuiet swallowed " + ex);
            ex.printStackTrace();
        }
    }

    // this will index "related" objects as needed
    // should be overridden by class-specific code
    public void opensearchUnindexDeep()
    throws IOException {
        this.opensearchUnindex();
    }

    // should be overridden
    public void opensearchDocumentSerializer(JsonGenerator jgen)
    throws IOException, JsonProcessingException {
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("BaseSerializer");
        myShepherd.beginDBTransaction();
        jgen.writeStringField("id", this.getId());
        jgen.writeNumberField("version", this.getVersion());

        jgen.writeFieldName("viewUsers");
        jgen.writeStartArray();
        for (String id : this.userIdsWithViewAccess(myShepherd)) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();

        jgen.writeFieldName("editUsers");
        jgen.writeStartArray();
        for (String id : this.userIdsWithEditAccess(myShepherd)) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }

    public static JSONObject opensearchQuery(final String indexname, final JSONObject query,
        int numFrom, int pageSize, String sort, String sortOrder)
    throws IOException {
        OpenSearch opensearch = new OpenSearch();
        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);

        return res;
    }

    public static Map<String, Long> getAllVersions(Shepherd myShepherd, String sql) {
        Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        Map<String, Long> rtn = new HashMap<String, Long>();
        List results = (List)query.execute();
        Iterator it = results.iterator();

        while (it.hasNext()) {
            Object[] row = (Object[])it.next();
            String id = (String)row[0];
            Long version = (Long)row[1];
            if (Util.stringExists(id)) {
                rtn.put(id, version);
            } else {
                System.out.println("WARNING: getAllVersions() skipping empty id; " + row);
            }
        }
        query.closeAll();
        return rtn;
    }

    public static Base createFromApi(JSONObject payload, List<File> files) throws ApiException {
        throw new ApiException("not yet supported");
    }

    // TODO should this be an abstract? will we need some base stuff?
    public static Object validateFieldValue(String fieldName, JSONObject data) throws ApiException {
        return null;
    }

/*
    public static JSONObject opensearchQuery(final JSONObject scrollData)
    throws IOException {
        OpenSearch opensearch = new OpenSearch();
        JSONObject res = opensearch.queryRawScroll(scrollData);

        return res;
    }
 */
}
