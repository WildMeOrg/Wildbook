package org.ecocean;

import org.json.JSONObject;
import java.util.*;
//import org.apache.commons.lang3.builder.ToStringBuilder;


public class Decision {

    private int id;
    private User user;
    private Encounter encounter;
    private long timestamp;
    private String property;
    private String value;

    public Decision() {
        this.timestamp = System.currentTimeMillis();
    }
    public Decision(User user, Encounter enc, String property, JSONObject value) {
        this.timestamp = System.currentTimeMillis();
        this.user = user;
        this.encounter = enc;
        this.property = property;
        if (value != null) this.value = value.toString();
    }

    public int getId() {
        return id;
    }

    public User getUser() {
        return user;
    }
    public void setUser(User u) {
        user = u;
    }

    public Encounter getEncounter() {
        return encounter;
    }
    public void setEncounter(Encounter enc) {
        encounter = enc;
    }

    public String getProperty() {
        return property;
    }
    public void setProperty(String prop) {
        property = prop;
    }

    public JSONObject getValue() {
        return Util.stringToJSONObject(value);
    }
    public String getValueAsString(){
      return value;
    }

    public void setValue(JSONObject val) {
        if (val == null) {
            value = null;
        } else {
            value = val.toString();
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static void updateEncounterStateBasedOnDecision(Shepherd myShepherd, Encounter enc){
      String context="context0";
      List<Decision> decisionsForEncounter = myShepherd.getDecisionsForEncounter(enc);
      if(decisionsForEncounter != null && decisionsForEncounter.size() > 0){
        int MIN_DECISIONS_TO_CHANGE_ENC_STATE = (new Integer(CommonConfiguration.getProperty("MIN_DECISIONS_TO_CHANGE_ENC_STATE",context))).intValue();
        int numberOfMatchDecisionsMadeForEncounter = Decision.getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter);
        System.out.println("Decision numberOfMatchDecisionsMadeForEncounter is: " + numberOfMatchDecisionsMadeForEncounter);
        if(getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter) >= MIN_DECISIONS_TO_CHANGE_ENC_STATE){
          System.out.println("Decision " + getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter) + " decisions have been made about the ecounter, which is at or above the " + MIN_DECISIONS_TO_CHANGE_ENC_STATE + " count threshold.");
          int numberOfAgreementsForMostAgreedUponMatch = Decision.getNumberOfAgreementsForMostAgreedUponMatch(decisionsForEncounter);
          int MIN_AGREEMENTS_TO_CHANGE_ENC_STATE = (new Integer(CommonConfiguration.getProperty("MIN_AGREEMENTS_TO_CHANGE_ENC_STATE",context))).intValue();
          if(numberOfAgreementsForMostAgreedUponMatch >= MIN_AGREEMENTS_TO_CHANGE_ENC_STATE){
            System.out.println("Decision numberOfAgreementsForMostAgreedUponMatch is: " + numberOfAgreementsForMostAgreedUponMatch + ", which is at or above the "+ MIN_AGREEMENTS_TO_CHANGE_ENC_STATE + " count threshold");
            try{
              String newState = "mergereview";
              enc.setState(newState);
              myShepherd.updateDBTransaction();
            }catch(Exception e){
              System.out.println("Error trying to update encounter state in Decision.updateEncounterStateBasedOnDecision()");
              e.printStackTrace();
            }
            finally{
              myShepherd.rollbackDBTransaction();
            }
          }else{
            if(numberOfAgreementsForMostAgreedUponMatch < MIN_AGREEMENTS_TO_CHANGE_ENC_STATE){
              System.out.println("Decision numberOfAgreementsForMostAgreedUponMatch is: " + numberOfAgreementsForMostAgreedUponMatch + ", which is below the "+ MIN_AGREEMENTS_TO_CHANGE_ENC_STATE + " count threshold. This means that the encounter decisions are disputed");
              try{
                String newState = "disputed";
                enc.setState(newState);
                myShepherd.updateDBTransaction();
              }catch(Exception e){
                System.out.println("Error trying to update encounter state in Decision.updateEncounterStateBasedOnDecision()");
                e.printStackTrace();
              }finally{
                myShepherd.rollbackDBTransaction();
              }
            }
          }
        }else{
          System.out.println("Decision " + getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter) + " decisions have been made about the ecounter, which is below the " + MIN_DECISIONS_TO_CHANGE_ENC_STATE + " count threshold.");
          return;
        }
      }else{
        System.out.println("Decision no decisions have been made!");
        return;
      }
    }

    public static int getNumberOfAgreementsForMostAgreedUponMatch(List<Decision> decisionsForEncounter){
      int numAgreements = 0;
      String currentMatchedMarkedIndividualId = null;
      int currentMatchedMarkedIndividualCounter = 0;
      JSONObject winningIndividualTracker = new JSONObject();
      JSONObject currentDecisionValue = new JSONObject();
      if(decisionsForEncounter!=null && decisionsForEncounter.size()>0){
        for(Decision currentDecision: decisionsForEncounter){
          if(currentDecision.getProperty().equals("match")){
            currentDecisionValue = currentDecision.getValue();
            currentMatchedMarkedIndividualId = currentDecisionValue.optString("id", null);
            currentMatchedMarkedIndividualCounter = winningIndividualTracker.optInt(currentMatchedMarkedIndividualId, 0);
            winningIndividualTracker.put(currentMatchedMarkedIndividualId, currentMatchedMarkedIndividualCounter+1);
          }
        }
        String winningMarkedIndividualId = findWinner(winningIndividualTracker);
        if(winningMarkedIndividualId!=null){
          numAgreements = winningIndividualTracker.optInt(winningMarkedIndividualId, 0);
        }
      }
      return numAgreements;
    }

    public static String findWinner(JSONObject winningIndividualTracker) {
      int currentMax = 0;
      String currentWinner = null;
      Iterator<String> keys = winningIndividualTracker.keys();
      String key = null;
      while(keys.hasNext()) {
          key = keys.next();
          if(winningIndividualTracker.optInt(key,0)>currentMax){
            currentMax = winningIndividualTracker.optInt(key,0);
            currentWinner = key;
          }
      }
      return currentWinner;
    }

    public static int getNumberOfMatchDecisionsMadeForEncounter(List<Decision> decisionsForEncounter){
      int numAgreements = 0;
      if(decisionsForEncounter!=null && decisionsForEncounter.size()>0){
        for(Decision currentDecision: decisionsForEncounter){
          if(currentDecision.getProperty().equals("match")){
            numAgreements ++;
          }
        }
      }
      return numAgreements;
    }

/*
    public String toString() {
        return new ToStringBuilder(this)
                .append(indexname)
                .append(readableName)
                .toString();
    }
*/

}
