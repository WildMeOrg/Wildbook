
package org.ecocean.servlet.importer;

import java.util.List;
import java.util.ArrayList;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.joda.time.DateTime;
import org.json.JSONObject;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ImportTask implements java.io.Serializable {

    private String id;
    private User creator;
    private DateTime created;
    private List<Encounter> encounters;
    private String parameters;

    public ImportTask() {
        this((User)null);
    }
    public ImportTask(User u) {
        this.creator = u;
        this.updateCreated();
        this.id = Util.generateUUID();
    }

    public String getId() {
        return id;
    }
    public void updateCreated() {
        created = new DateTime();
    }
    public DateTime getCreated() {
        return created;
    }

    public List<Encounter> getEncounters() {
        return encounters;
    }
    public void setEncounters(List<Encounter> encs) {
        encounters = encs;
    }

    public void setCreator(User u) {
        creator = u;
    }
    public User getCreator() {
        return creator;
    }

    //TODO should we consider occ.assets ?
    public List<MediaAsset> getMediaAssets() {
        if (encounters == null) return null;
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (Encounter enc : encounters) {
            ArrayList<MediaAsset> encMAs = enc.getMedia();
            if (Util.collectionSize(encMAs) > 0) for (MediaAsset ma : encMAs) {
                if (!mas.contains(ma)) mas.add(ma);  //dont want duplicates
            }
        }
        return mas;
    }

    public List<Occurrence> getOccurrences(Shepherd myShepherd) {
        if (encounters == null) return null;
        List<Occurrence> occs = new ArrayList<Occurrence>();
        for (Encounter enc : encounters) {
            String occId = enc.getOccurrenceID();
            if (occId == null) continue;
            Occurrence occ = myShepherd.getOccurrence(occId);
            if (occ != null) occs.add(occ);
        }
        return occs;
    }

    public void setParameters(String s) {
        parameters = s;
    }
    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;
        } else {
            parameters = j.toString();
        }
    }
    public String getParametersAsString() {
        return parameters;
    }
    public JSONObject getParameters() {
        return Util.stringToJSONObject(parameters);
    }

    public void setPassedParameters(HttpServletRequest request) {
        JSONObject p = getParameters();
        if (p == null) p = new JSONObject();
        p.put("_passedParameters", Util.requestParametersToJSONObject(request));
        parameters = p.toString();
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("created", created)
                .append("creator", (creator == null) ? (String)null : creator.getDisplayName())
                .append("numEncs", Util.collectionSize(encounters))
                .toString();
    }


}
