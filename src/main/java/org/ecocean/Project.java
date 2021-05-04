package org.ecocean;

import java.util.List;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class Project implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private List<Encounter> encounters = null;

    private String researchProjectName;
    private String projectIdPrefix;

    private Long dateCreatedLong;
    private Long dateLastModifiedLong;

    //reference to a user in the user array, so we don't link to that table twice
    private String ownerId;
    private List<User> users = null;

    private int nextIndividualIdIncrement = 0;

    //empty constructor used by the JDO enhancer
    public Project() {}

    public Project(final String projectIdPrefix) {
        this(projectIdPrefix, null, null);
    }

    public Project(final String projectIdPrefix, final List<Encounter> encs) {
        this(projectIdPrefix, null, encs);
    }

    public Project(final String projectIdPrefix, final String researchProjectName) {
        this(projectIdPrefix, researchProjectName, null);
        System.out.println("the correct constructor is called");
    }

    public Project(final String projectIdPrefix, final String researchProjectName, final List<Encounter> encs) {
        this.encounters = new ArrayList<>();
        this.id = Util.generateUUID();
        this.projectIdPrefix = projectIdPrefix;
        this.researchProjectName = researchProjectName;
        setTimeCreated();
        setTimeLastModified();
    }

    public String getId() {
        return id;
    }

    public int getNextIndividualIdIncrement() {
        return nextIndividualIdIncrement;
    }

    public String getNextIncrementalIndividualIdAndAdvance() {
        String nextId = getNextIncrementalIndividualId();
        nextIndividualIdIncrement++;
        setTimeLastModified();
        return nextId;
    }

    public String getNextIncrementalIndividualId() {
        if (!projectIdPrefix.contains("#")) {
            return projectIdPrefix + nextIndividualIdIncrement;
        }
        String nextName = projectIdPrefix.replace("#","0");
        int poundCount = StringUtils.countMatches(projectIdPrefix,"#");
        int currentDigits = String.valueOf(nextIndividualIdIncrement).length();
        if (poundCount>currentDigits) {
            nextName = nextName.substring(0, nextName.length() - currentDigits);
        } else {
            nextName = nextName.replace("0", "");
        }
        return nextName+nextIndividualIdIncrement;
    }

    public void adjustIncrementalIndividualId(int adjustment) {
        System.out.println("[WARN]: Project Individual Id Increment for "+projectIdPrefix+" has been adjusted by "+adjustment+", likely for error handling.");
        nextIndividualIdIncrement = nextIndividualIdIncrement + adjustment;
        setTimeLastModified();
    }

    public Double getPercentWithIncrementalIds(){
      double numEncounters = encounters.size();
      double hasIncrementalId = 0.0;
      for (Encounter enc : encounters) {
          if (enc.getIndividual()!=null) {
              MarkedIndividual thisIndividual = enc.getIndividual();
              if (thisIndividual.hasNameKey(getProjectIdPrefix())) {
                  hasIncrementalId++;
              }
          }
      }
      if (numEncounters==0.0||hasIncrementalId==0.0) return 0.0;
      double percentWithIncrementalIds = 100 * (hasIncrementalId / numEncounters);
      return Math.round(percentWithIncrementalIds * 10.0)/10.0;
  }

    public  Double getPercentIdentified(Shepherd myShepherd){
        if (numEncounters()>0&&numIndividuals(myShepherd)>0) {
            double numIncremented = nextIndividualIdIncrement;
            double numEncounters = numEncounters();
            if(numEncounters>0){ // avoid potential divide by zero error
              return (Double) Math.floor(100.0 * numIncremented/numEncounters);
            }
        }
        return (Double) 0.0;
    }

    public void addUser(User user) {
        List<User> userArr = new ArrayList<User>();
        if (user!=null) {
            userArr.add(user);
            addUsers(userArr);
            setTimeLastModified();
        }
    }

    public void addUsers(List<User> users) {
        if (users!=null) {
            if (this.users==null) {
                this.users = new ArrayList<>();
            }
            for (User user : users) {
                if (!this.users.contains(user)) {
                    this.users.add(user);
                    setTimeLastModified();
                }
            }
        } else {
            System.out.println("[WARN]: Project.addUser() or addUsers() for "+projectIdPrefix+" was passed a null.");
        }
    }

    public void removeUser(User user) {
        if (users.contains(user)) {
            users.remove(user);
            setTimeLastModified();
        }
    }

    public List<User> getUsers() {
        return users;
    }

    public void setOwner(User owner) {
        if (owner!=null) {
            addUser(owner);
            ownerId = owner.getId();
            setTimeLastModified();
        }
    }

    public String getOwnerId() {
        return ownerId;
    }

    public User getOwner() {
        for (User user : users) {
            if (user.getId().equals(ownerId)) {
                return user;
            }
        }
        return null;
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
        setTimeLastModified();
        this.researchProjectName = researchProjectName;
    }

    public String getResearchProjectName() {
        if (researchProjectName==null || "".equals(researchProjectName)) return getProjectIdPrefix();
        return researchProjectName;
    }

    public void setProjectIdPrefix(final String projectIdPrefix) {
        setTimeLastModified();
        this.projectIdPrefix = projectIdPrefix;
    }

    public String getProjectIdPrefix() {
        return projectIdPrefix;
    }

    public List<Encounter> getEncounters() {
        return encounters;
    }

    public void addEncounter(final Encounter enc) {
        setTimeLastModified();
        if(encounters==null){
          encounters = new ArrayList<Encounter>();
        }
        if (enc != null && encounters!=null && !encounters.contains(enc)) {
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

    public int numEncounters() {
        if (encounters!=null) {
            return encounters.size();
        }
        return 0;
    }

    public int numIndividuals(Shepherd myShepherd) {
        List<MarkedIndividual> individuals = myShepherd.getMarkedIndividualsFromProject(this);
        if (individuals!=null) {
            return individuals.size();
        }
        return 0;
    }

    public JSONObject asJSONObject() {
        return asJSONObject(null, null);
    }

    public JSONObject asJSONObjectWithEncounterMetadata(Shepherd myShepherd) {
        return asJSONObject("addEncounterMetadata", myShepherd, null);
    }

    public JSONObject asJSONObjectWithEncounterMetadata(Shepherd myShepherd, HttpServletRequest request) {
        return asJSONObject("addEncounterMetadata", myShepherd, request);
    }

    private JSONObject asJSONObject(String modifier, Shepherd myShepherd) {
        return asJSONObject(modifier, myShepherd, null);
    }

    private JSONObject asJSONObject(String modifier, Shepherd myShepherd, HttpServletRequest request) {
        //System.out.println("Here json1");  
      
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("ownerId", ownerId);
        j.put("researchProjectName", researchProjectName);
        j.put("projectIdPrefix", projectIdPrefix);
        j.put("dateCreatedLong", dateCreatedLong);
        j.put("dateLastModifiedLong", dateLastModifiedLong);
        JSONArray usersJSONArr = new JSONArray();
        if(users!=null) {
          //System.out.println("Here json2");
          for (User user : users) {
              JSONObject userJSON = new JSONObject();
              userJSON.put("username", user.getUsername());
              userJSON.put("id", user.getId());
              usersJSONArr.put(userJSON);
          }
        }
        j.put("users", usersJSONArr);
        JSONArray encArr = new JSONArray();
        if (encounters!=null) {
          //System.out.println("Here json3");
            for (final Encounter enc : encounters) {
                if (modifier!=null&&"addEncounterMetadata".equals(modifier)) {
                  //System.out.println("Here json3a");  
                  JSONObject encMetadata = new JSONObject();
                    String individualName = "";
                    String individualUUID = "";
                    String individualProjectId = "";
                    boolean hasNameKeyMatchingProject = false;
                    MarkedIndividual individual = enc.getIndividual();
                    if (individual!=null&&Util.stringExists(individual.getDisplayName())) {
                        if (request!=null) {
                            individualName = individual.getDisplayName(request, myShepherd);
                        } else {
                            individualName = individual.getDisplayName();
                        }
                        individualUUID = individual.getId();
                        hasNameKeyMatchingProject = individual.hasNameKey(projectIdPrefix);
                        if (hasNameKeyMatchingProject) {
                            individualProjectId = individual.getDisplayName(projectIdPrefix);
                        }
                    }
                    //System.out.println("Here json3b");
                    encMetadata.put("individualUUID", individualUUID);
                    encMetadata.put("individualDisplayName", individualName);
                    encMetadata.put("hasNameKeyMatchingProject", hasNameKeyMatchingProject);
                    encMetadata.put("encounterDate", enc.getDate());
                    encMetadata.put("locationId", enc.getLocationID());
                    encMetadata.put("submitterId", enc.getSubmitterID());
                    encMetadata.put("encounterId", enc.getID());
                    encMetadata.put("individualProjectId", individualProjectId);
                    JSONArray allProjectIds = new JSONArray();
                    //WB-1615 turn off due to poor performance
                    /*
                    if(myShepherd.getProjectIdPrefixsForEncounter(enc)!=null) {
                      System.out.println("Here json4");
                      for (String projectId : myShepherd.getProjectIdPrefixsForEncounter(enc)) {
                          allProjectIds.put(projectId);
                      }
                    }
                    */
                    encMetadata.put("allProjectIds", allProjectIds);
                    //System.out.println("Here json5");
                    encArr.put(encMetadata);
                } else {
                  //System.out.println("Here json6");
                    encArr.put(enc.getID());
                }
            }
        }
        j.put("encounters", encArr);
        //System.out.println("Here json7");
        return j;
    }

    public JSONArray getAllACMIdsJSON() {
        JSONArray allACMIds = new JSONArray();
        List<String> allACMIDsStr = new ArrayList<String>();
        for (Encounter enc : encounters) {
            if (enc.hasAnnotations()) {
                List<Annotation> anns = enc.getAnnotations();
                for (Annotation ann : anns) {
                    if (ann.hasAcmId()&&!ann.isTrivial()&&!allACMIDsStr.contains(ann.getAcmId())){
                        allACMIDsStr.add(ann.getAcmId());
                        allACMIds.put(ann.getAcmId());
                    }
                }
            }
        }
        return allACMIds;
    }
    public JSONArray getAllAnnotIdsJSON() {
      JSONArray allAnnotIds = new JSONArray();
      List<String> allAnnotIDsStr = new ArrayList<String>();
      for (Encounter enc : encounters) {
          if (enc.hasAnnotations()) {
              List<Annotation> anns = enc.getAnnotations();
              for (Annotation ann : anns) {
                  if (!ann.isTrivial()&&!allAnnotIDsStr.contains(ann.getId())){
                      allAnnotIDsStr.add(ann.getId());
                      allAnnotIds.put(ann.getId());
                  }
              }
          }
      }
      return allAnnotIds;
  }

    public String toString() {
        return this.asJSONObject().toString();
    }

    public boolean doesUserOwnProject(Shepherd myShepherd, HttpServletRequest request) {
        User user = myShepherd.getUser(request);
        if (user!=null&&ownerId.equals(user.getId())) return true;
        return false;
    }

}
