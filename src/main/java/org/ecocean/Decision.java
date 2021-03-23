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

    public static void updateEncounterStateBasedOnDecision(Shepherd myShepherd, Encounter enc, List<String> skipUsers){
      String context="context0";
      List<Decision> decisionsForEncounter = myShepherd.getDecisionsForEncounter(enc);
      if(decisionsForEncounter != null && decisionsForEncounter.size() > 0){
        // System.out.println("updateEncounterStateBasedOnDecision decisions nonzero");
        Double MIN_DECISIONS_TO_CHANGE_ENC_STATE = (new Double(CommonConfiguration.getProperty("MIN_DECISIONS_TO_CHANGE_ENC_STATE",context))).doubleValue();
        Double numberOfMatchDecisionsMadeForEncounter = Decision.getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter, skipUsers);
        if(numberOfMatchDecisionsMadeForEncounter == 0) return; //avoid divide by zero errors
        // System.out.println("Decision numberOfMatchDecisionsMadeForEncounter is: " + numberOfMatchDecisionsMadeForEncounter);
        if(getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter, skipUsers) >= MIN_DECISIONS_TO_CHANGE_ENC_STATE){
          // System.out.println("Decision " + getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter) + " decisions have been made about the ecounter, which is at or above the " + MIN_DECISIONS_TO_CHANGE_ENC_STATE + " count threshold.");
          Double numberOfAgreementsForMostAgreedUponMatch = Decision.getNumberOfAgreementsForMostAgreedUponMatch(decisionsForEncounter, skipUsers);
          Double agreementRatio = numberOfAgreementsForMostAgreedUponMatch/numberOfMatchDecisionsMadeForEncounter;
          // System.out.println("agreementRatio is: " + agreementRatio);
          Double MIN_AGREEMENTS_TO_CHANGE_ENC_STATE = (new Double(CommonConfiguration.getProperty("MIN_AGREEMENTS_TO_CHANGE_ENC_STATE",context))).doubleValue();
          Double ratioThreshold = MIN_AGREEMENTS_TO_CHANGE_ENC_STATE/MIN_DECISIONS_TO_CHANGE_ENC_STATE;
          if(agreementRatio >= ratioThreshold){
            // System.out.println("MIN_AGREEMENTS_TO_CHANGE_ENC_STATE is: " + MIN_AGREEMENTS_TO_CHANGE_ENC_STATE);
            // System.out.println("MIN_DECISIONS_TO_CHANGE_ENC_STATE is: " + MIN_DECISIONS_TO_CHANGE_ENC_STATE);
            // System.out.println("ratioThreshold is: " + ratioThreshold);
            System.out.println("Decision for encounter " + enc.getCatalogNumber() + ": agreementRatio is: " + agreementRatio + ", which is at or above the "+ ratioThreshold + " percentage threshold. Setting to mergereview...");
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
            if(agreementRatio < ratioThreshold){
              System.out.println("Decision for encounter " + enc.getCatalogNumber() + ": agreementRatio is: " + agreementRatio + ", which is below the "+ ratioThreshold + " percentage threshold. This means that the encounter decisions are disputed. Setting to disputed...");
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
          System.out.println("Decision " + getNumberOfMatchDecisionsMadeForEncounter(decisionsForEncounter, skipUsers) + " decisions have been made about the ecounter, which is below the " + MIN_DECISIONS_TO_CHANGE_ENC_STATE + " count threshold.");
          return;
        }
      }else{
        // System.out.println("Decision no decisions have been made!");
        return;
      }
    }

    public static Double getNumberOfAgreementsForMostAgreedUponMatch(List<Decision> decisionsForEncounter, List<String> skipUsers){
      Double numAgreements = 0.0;
      JSONObject winningIndividualTracker = new JSONObject();
      winningIndividualTracker = populateIdsWithMatchCounts(decisionsForEncounter, skipUsers);
      String winningMarkedIndividualId = findWinner(winningIndividualTracker);
      if(winningMarkedIndividualId!=null){
        numAgreements = winningIndividualTracker.optDouble(winningMarkedIndividualId, 0.0);
      }
      return numAgreements;
    }

    public static JSONObject populateIdsWithMatchCounts(List<Decision> decisionsForEncounter, List<String> skipUsers){
      String currentMatchedMarkedIndividualId = null;
      Double currentMatchedMarkedIndividualCounter = 0.0;
      JSONObject idsWithMatchCounts = new JSONObject();
      JSONObject currentDecisionValue = new JSONObject();
      if(decisionsForEncounter!=null && decisionsForEncounter.size()>0){
        for(Decision currentDecision: decisionsForEncounter){
          if(currentDecision.getProperty().equals("match")){
            if (currentDecision.getUser() == null) { // I guess allow this?
              currentDecisionValue = currentDecision.getValue();
              currentMatchedMarkedIndividualId = currentDecisionValue.optString("id", null);
              currentMatchedMarkedIndividualCounter = idsWithMatchCounts.optDouble(currentMatchedMarkedIndividualId, 0.0);
              idsWithMatchCounts.put(currentMatchedMarkedIndividualId,currentMatchedMarkedIndividualCounter + 1);
            }
            if (currentDecision.getUser() != null && !skipUsers.contains(currentDecision.getUser().getUsername())) {
              currentDecisionValue = currentDecision.getValue();
              currentMatchedMarkedIndividualId = currentDecisionValue.optString("id", null);
              currentMatchedMarkedIndividualCounter = idsWithMatchCounts.optDouble(currentMatchedMarkedIndividualId, 0.0);
              idsWithMatchCounts.put(currentMatchedMarkedIndividualId,currentMatchedMarkedIndividualCounter + 1);
            }
            
          }
        }
      }
      return idsWithMatchCounts;
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

    public static List<String> getEncounterIdsOfMostAgreedUponMatches(List<Decision> decisionsForEncounter, List<String> skipUsers){
      List<String> matchedIds = new ArrayList<String>();
      JSONObject idsWithMatchCounts = new JSONObject();
      if(decisionsForEncounter!=null && decisionsForEncounter.size()>0){
        idsWithMatchCounts = populateIdsWithMatchCounts(decisionsForEncounter, skipUsers);
        matchedIds = sortIdsByPopularity(idsWithMatchCounts);
      }
      return matchedIds;
    }

    public static List<Integer> getNumberOfVotesForMostAgreedUponMatchesInParallelOrder(List<Decision> decisionsForEncounter, List<String> skipUsers) {
      List<Integer> numberOfVotes = new ArrayList<Integer>();
      JSONObject idsWithMatchCounts = new JSONObject();
      if (decisionsForEncounter != null && decisionsForEncounter.size() > 0) {
        idsWithMatchCounts = populateIdsWithMatchCounts(decisionsForEncounter, skipUsers);
        numberOfVotes = sortVotesByPopularity(idsWithMatchCounts);
      }
      return numberOfVotes;
    }

    public static List<Integer> sortVotesByPopularity(JSONObject idsWithMatchCounts) {
      List<Integer> votes = new ArrayList<Integer>();
      int minVotesToBeIncluded = 1;
      Iterator<String> keys = idsWithMatchCounts.keys();
      String key = null;
      while (keys.hasNext()) {
        key = keys.next();
        if (idsWithMatchCounts.optInt(key, 0) >= minVotesToBeIncluded) {
          votes.add(idsWithMatchCounts.optInt(key, 0));
        }
      }
      Collections.sort(votes);
      Collections.reverse(votes);
      return votes;
    }

    public static List<String> sortIdsByPopularity(JSONObject idsWithMatchCounts){
      List<String> resultsArr = new ArrayList<String>();
      List<Integer> votes = new ArrayList<Integer>();
      List<String> idsInParallelWithVotes = new ArrayList<String>();
      int minVotesToBeIncluded = 1;
      Iterator<String> keys = idsWithMatchCounts.keys();
      String key = null;
      while(keys.hasNext()) {
          key = keys.next();
          if(idsWithMatchCounts.optInt(key,0) >= minVotesToBeIncluded){
            votes.add(idsWithMatchCounts.optInt(key,0));
            idsInParallelWithVotes.add(key);
          }
      }
      final List<String> stringListCopy = new ArrayList(idsInParallelWithVotes);
      ArrayList<String> sortedList = new ArrayList(stringListCopy);
      int[] votesPrimitive = convertIntegers(votes);
      Collections.sort(sortedList, Comparator.comparing(s -> votesPrimitive[stringListCopy.indexOf(s)])); //ugh votesPrimitive array needs to be primitive gross.
      Collections.reverse(sortedList); // want it in descending order
      if(sortedList!=null && sortedList.size()>0) resultsArr = sortedList;
      return resultsArr;
    }


    public static int[] convertIntegers(List<Integer> integers){
      int[] ret = null;
      if(integers!=null && integers.size()>0){
        ret = new int[integers.size()];
        for (int i=0; i < ret.length; i++)
        {
          ret[i] = integers.get(i).intValue();
        }
      }
      return ret;
    }

    public static Double getNumberOfMatchDecisionsMadeForEncounter(List<Decision> decisionsForEncounter, List<String> skipUsers){
      Double numAgreements = 0.0;
      if(decisionsForEncounter!=null && decisionsForEncounter.size()>0){
        for(Decision currentDecision: decisionsForEncounter){
          if(currentDecision.getProperty().equals("match")){
            if(currentDecision.getUser() == null){ // I guess allow this to increment?
              numAgreements ++;  
            }
            if(currentDecision.getUser() != null && !skipUsers.contains(currentDecision.getUser().getUsername())){
              numAgreements ++;
            }
          }
        }
      }
      return numAgreements;
    }

}
