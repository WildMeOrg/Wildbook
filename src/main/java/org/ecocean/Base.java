package org.ecocean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.List;
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

        opensearch.createIndex(opensearchIndexName());
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
    }

    public static JSONObject opensearchQuery(final String indexname, final JSONObject query,
        int pageSize)
    throws IOException {
        OpenSearch opensearch = new OpenSearch();
        JSONObject res = opensearch.queryRawScroll(indexname, query, pageSize);

        return res;
    }

    public static JSONObject opensearchQuery(final JSONObject scrollData)
    throws IOException {
        OpenSearch opensearch = new OpenSearch();
        JSONObject res = opensearch.queryRawScroll(scrollData);

        return res;
    }
}
