package org.ecocean;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;

public class Project implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private ArrayList<Encounter> encounters = null;

    private String researchProjectName;
    private String researchProjectId;

    private Long dateCreatedLong;
    private Long dateLastModifiedLong;

    private String ownerId;

    //empty constructor used by the JDO enhancer
    public Project() {}

    public Project(final String researchProjectId) {
        this(researchProjectId, null, null);
    }

    public Project(final String researchProjectId, final List<Encounter> encs) {
        this(researchProjectId, null, encs);
    }

    public Project(final String researchProjectId, final String researchProjectName) {
        this(researchProjectId, researchProjectName, null);
    }

    public Project(final String researchProjectId, final String researchProjectName, final List<Encounter> encs) {
        this.encounters = new ArrayList<>();
        this.id = Util.generateUUID();
        this.researchProjectId = researchProjectId;
        this.researchProjectName = researchProjectName;
        setTimeCreated();
        setTimeLastModified();
    }

    public String getId() {
        return id;
    }

    public void setOwner(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    private void setTimeCreated() {
        dateCreatedLong = System.currentTimeMillis();
    }

    public void setTimeLastModified() {
        dateLastModifiedLong = System.currentTimeMillis();
    }

    public long getDataCreatedLong() {
        return dateCreatedLong;
    }

    public long getTimeLastModifiedLong() {
        return dateLastModifiedLong;
    }

    public void setResearchProjectName(final String researchProjectname) {
        setTimeLastModified();
        this.researchProjectName = researchProjectname;
    }

    public String getResearchProjectName() {
        return researchProjectName;
    }

    public void setResearchProjectId(final String researchProjectId) {
        setTimeLastModified();
        this.researchProjectId = researchProjectId;
    }

    public String getResearchProjectId() {
        return researchProjectId;
    }

    public List<Encounter> getEncounters() {
        return encounters;
    }

    public void addEncounter(final Encounter enc) {
        setTimeLastModified();
        if (!encounters.contains(enc)) {
            encounters.add(enc);
        } else {
            System.out.println("[INFO]: Project.addEncounter(): The selected Project id="+id+" already contains encounter id="+enc.getID()+", skipping.");
        }
    }

    public void addEncounters(final List<Encounter> encs) {
        for (final Encounter enc : encs) {
            addEncounter(enc);
        }   
    }

    public void removeEncounter(final Encounter enc) {
        setTimeLastModified();
        encounters.remove(enc);
    }

    public void clearAllEncounters() {
        setTimeLastModified();
        encounters = new ArrayList<>();
    }

    public List<MarkedIndividual> getAllIndividualsForProject() {
        ArrayList<MarkedIndividual> mis = null;
        for (final Encounter enc : encounters) {
            final MarkedIndividual mi = enc.getIndividual();
            if (mi!=null) {
                if (mis==null) {
                    mis = new ArrayList<>();
                }
                if (!mis.contains(mi)) {
                    mis.add(mi);
                }
            }
        }
        return mis;
    }

    public int numEncounters() {
        if (encounters!=null) {
            return encounters.size();
        }
        return 0;
    }

    public int numIndividuals() {
        if (getAllIndividualsForProject()!=null) {
            return getAllIndividualsForProject().size();
        }
        return 0;
    }

    public JSONObject asJSONObject() {
        JSONObject j = new JSONObject();
        j.put("ownerId", ownerId);
        j.put("researchProjectName", researchProjectName);
        j.put("researchProjectId", researchProjectId);
        j.put("dateCreatedLong", researchProjectId);
        j.put("dateLastModifiedLong", researchProjectId);
        JSONArray encArr = new JSONArray();
        for (Encounter enc : encounters) {
            encArr.put(enc.getID());
        }
        j.put("encounters", encArr);
        return j;
    }

    public String toString() {
        return this.asJSONObject().toString();
    }

    public boolean doesUserOwnProject(Shepherd myShepherd, HttpServletRequest request) {
        User user = myShepherd.getUser(request);
        if (ownerId.equals(user.getId())) return true;
        return false;
    }
    
}