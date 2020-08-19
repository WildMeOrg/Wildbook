package org.ecocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

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

    private int individualIdIncrement = 0;

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
        System.out.println("the correct constructor is called");
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

    public int getNextIndividualIdIncrement() {
        return individualIdIncrement;
    }

    public String getNextIncrementalIndividualId() {
        String nextId = researchProjectId + individualIdIncrement;
        individualIdIncrement++;
        return nextId;
    }

    public  Double getPercentIdentified(){
        if (numEncounters()>0&&numIndividuals()>0) {
            double numIndividuals = numIndividuals();
            double numEncounters = numEncounters();
            return (Double) numIndividuals/numEncounters;
        }
        return (Double) 0.0;
    }

    //this value is already returned with numIndividuals()
    public Integer getNumberOfIndividuals(){
      return 15;
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

    public void setResearchProjectName(final String researchProjectName) {
      System.out.println("getresearchProjectName called");
      System.out.println("researchProjectName is: " + researchProjectName);
        setTimeLastModified();
        this.researchProjectName = researchProjectName;
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
        this.encounters = new ArrayList<>();
    }

    // TODO will need some solid testing to make sure database fetch gets individuals with encounters in all cases
    public List<MarkedIndividual> getAllIndividualsForProject() {
        ArrayList<MarkedIndividual> mis = null;
        if (encounters!=null) {
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
        j.put("id", id);
        j.put("ownerId", ownerId);
        j.put("researchProjectName", researchProjectName);
        j.put("researchProjectId", researchProjectId);
        j.put("dateCreatedLong", dateCreatedLong);
        j.put("dateLastModifiedLong", dateLastModifiedLong);
        JSONArray encArr = new JSONArray();
        if (encounters!=null) {
            for (final Encounter enc : encounters) {
                encArr.put(enc.getID());
            }
        }
        j.put("encounters", encArr);
        return j;
    }

    public String toString() {
        return this.asJSONObject().toString();
    }

    public boolean doesUserOwnProject(Shepherd myShepherd, HttpServletRequest request) {
        User user = myShepherd.getUser(request);
        if (user!=null&&ownerId.equals(user.getId())) return true;
        return false;
    }

    //stub TODO
    public static List<Project> getProjectsForUser(User user){
      Project proj1 = new Project("ID1", "Project1");
      System.out.println(proj1.toString());
      Project proj2 = new Project("ID2", "Project2");
      Project proj3 = new Project("ID3", "Project3");
      Project proj4 = new Project("ID4", "Project4");
      Project proj5 = new Project("ID5", "Project5");
      ArrayList<Project> projects = new ArrayList<Project>(Arrays.asList(proj1, proj2, proj3, proj4, proj5));
      // ArrayList<Project> projects = new ArrayList<Project>();
      return projects;
    }

}
