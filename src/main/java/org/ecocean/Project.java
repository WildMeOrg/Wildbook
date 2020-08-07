package org.ecocean;

import java.util.ArrayList;
import java.util.List;

public class Project implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id = Util.generateUUID();

    private List<Encounter> encounters = new ArrayList<>();

    private String researchProjectname;
    private String researchProjectId;

    private Long dateCreatedLong;
    private Long dateLastModifiedLong;

    //empty constructor used by the JDO enhancer
    public Project() {}

    public Project(String researchProjectId) {
        this(researchProjectId, null, null);
    }

    public Project(String researchProjectId, List<Encounter> encs) {
        this(researchProjectId, null, encs);
    }

    public Project(String researchProjectId, String researchProjectName) {
        this(researchProjectId, researchProjectName, null);
    }

    public Project(String researchProjectId, String researchProjectName, List<Encounter> encs) {
        setTimeCreated();
        setTimeLastModified();
        this.researchProjectId = researchProjectId;
    }

    public String getId() {
        return this.id;
    }

    private void setTimeCreated() {
        this.dateCreatedLong = System.currentTimeMillis();
    }

    public void setTimeLastModified() {
        this.dateLastModifiedLong = System.currentTimeMillis();
    }

    public long getDataCreatedLong() {
        return this.dateCreatedLong;
    }

    public long getTimeLastModifiedLong() {
        return this.dateLastModifiedLong;
    }

    public void setResearchProjectName(String name) {
        this.researchProjectname = name;
    }

    public String getResearchProjectName() {
        return this.researchProjectname;
    }

    public void setResearchProjectId(String id) {
        this.researchProjectId = id;
    }

    public String getResearchProjectId() {
        return this.researchProjectId;
    }

    public List<Encounter> getEncounters() {
        return this.encounters;
    }

    public void addEncounter(Encounter enc) {
        this.encounters.add(enc);
    }

    public void addEncounters(List<Encounter> encs) {
        this.encounters.addAll(encs);
    }


    
}