package org.ecocean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
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

    // issue 785 makes this no longer necessary; the overrides are left on Occurrence and MarkedIndividual
    // for now as reference -- but are not called. they will need to be addressed when these classes are searchable
    // public abstract List<String> userIdsWithViewAccess(Shepherd myShepherd);
    // public abstract List<String> userIdsWithEditAccess(Shepherd myShepherd);

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
        map.put("viewUsers", new org.json.JSONObject("{\"type\": \"keyword\"}"));
        map.put("editUsers", new org.json.JSONObject("{\"type\": \"keyword\"}"));
        return map;
    }

    public void opensearchIndex()
    throws IOException {
        long startT = System.currentTimeMillis();
        OpenSearch opensearch = new OpenSearch();

        opensearch.index(this.opensearchIndexName(), this);
        long endT = System.currentTimeMillis();
        System.out.println("opensearchIndex(): " + (endT - startT) + "ms indexing " + this);
    }

    // this will index "related" objects as needed
    // should be overridden by class-specific code
    public void opensearchIndexDeep()
    throws IOException {
        this.opensearchIndex();
    }

    public void opensearchUnindex()
    throws IOException {
        // unindexing should be non-blocking and backgrounded
        String opensearchIndexName = this.opensearchIndexName();
        String objectId = this.getId();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Runnable rn = new Runnable() {
            OpenSearch opensearch = new OpenSearch();
            public void run() {
                try {
                    opensearch.delete(opensearchIndexName, objectId);
                } catch (Exception e) {
                    System.out.println("opensearchUnindex() backgrounding Object " + objectId +
                        " hit an exception.");
                    e.printStackTrace();
                }
                executor.shutdown();
            }
        };

        executor.execute(rn);
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

    public void opensearchUpdate(final JSONObject updateData)
    throws IOException {
        if (updateData == null) return;
        OpenSearch opensearch = new OpenSearch();

        opensearch.indexUpdate(this.opensearchIndexName(), this.getId(), updateData);
    }

    // should be overridden
    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
    throws IOException, JsonProcessingException {
        jgen.writeStringField("id", this.getId());
        jgen.writeNumberField("version", this.getVersion());
        jgen.writeNumberField("indexTimestamp", System.currentTimeMillis());

/*
        these are no longer computed in the general opensearchIndex() call.
        they are too expensive. see Encounter.opensearchIndexPermission()

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
 */
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen)
    throws IOException, JsonProcessingException {
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("BaseSerializer");
        myShepherd.beginDBTransaction();
        try {
            opensearchDocumentSerializer(jgen, myShepherd);
        } catch (Exception e) {} finally {
            myShepherd.rollbackAndClose();
        }
    }

    public static JSONObject opensearchQuery(final String indexname, final JSONObject query,
        int numFrom, int pageSize, String sort, String sortOrder)
    throws IOException {
        OpenSearch opensearch = new OpenSearch();
        JSONObject res = opensearch.queryPit(indexname, query, numFrom, pageSize, sort, sortOrder);

        return res;
    }

    // this is so we can call it on Base obj, but really is only needed by [overridden by] Encounter (currently)
    public boolean getOpensearchProcessPermissions() {
        return false;
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

    // these two methods are kinda hacky needs for opensearchSyncIndex (e.g. the fact
    // they are not static)
    public abstract Base getById(Shepherd myShepherd, String id);

    public abstract String getAllVersionsSql();

    // contains some reflection; not pretty, but gets the job done
    public static int[] opensearchSyncIndex(Shepherd myShepherd, Class cls, int stopAfter)
    throws IOException {
        int[] rtn = new int[2];
        Object tmpObj = null;

        try {
            tmpObj = cls.newInstance();
        } catch (Exception ex) {
            throw new IOException("FAIL: " + ex);
        }
        Base baseObj = (Base)tmpObj;
        String indexName = baseObj.opensearchIndexName();
        if (OpenSearch.indexingActive()) {
            System.out.println("Base.opensearchSyncIndex(" + indexName +
                ") skipped due to indexingActive()");
            rtn[0] = -1;
            rtn[1] = -1;
            return rtn;
        }
        OpenSearch.setActiveIndexingBackground();
        OpenSearch os = new OpenSearch();
        List<List<String> > changes = os.resolveVersions(getAllVersions(myShepherd,
            baseObj.getAllVersionsSql()), os.getAllVersions(indexName));
        if (changes.size() != 2) throw new IOException("invalid resolveVersions results");
        List<String> needIndexing = changes.get(0);
        List<String> needRemoval = changes.get(1);
        rtn[0] = needIndexing.size();
        rtn[1] = needRemoval.size();
        System.out.println("Base.opensearchSyncIndex(" + indexName + "): stopAfter=" + stopAfter +
            ", needIndexing=" + rtn[0] + ", needRemoval=" + rtn[1]);
        int ct = 0;
        for (String id : needIndexing) {
            Base obj = baseObj.getById(myShepherd, id);
            try {
                if (obj != null) os.index(indexName, obj);
            } catch (Exception ex) {
                System.out.println("Base.opensearchSyncIndex(" + indexName + "): index failed " +
                    obj + " => " + ex.toString());
                ex.printStackTrace();
            }
            if (ct % 500 == 0)
                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needIndexing: " +
                    ct + "/" + rtn[0]);
            ct++;
            if ((stopAfter > 0) && (ct > stopAfter)) {
                System.out.println("Base.opensearchSyncIndex(" + indexName +
                    ") breaking due to stopAfter");
                break;
            }
        }
        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needIndexing");
        ct = 0;
        for (String id : needRemoval) {
            os.delete(indexName, id);
            if (ct % 500 == 0)
                System.out.println("Base.opensearchSyncIndex(" + indexName + ") needRemoval: " +
                    ct + "/" + rtn[1]);
            ct++;
        }
        System.out.println("Base.opensearchSyncIndex(" + indexName + ") finished needRemoval");
        OpenSearch.unsetActiveIndexingBackground();
        return rtn;
    }

    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
    throws ApiException {
        throw new ApiException("not yet supported");
    }

    // TODO should this be an abstract? will we need some base stuff?
    public static Object validateFieldValue(String fieldName, JSONObject data)
    throws ApiException {
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
