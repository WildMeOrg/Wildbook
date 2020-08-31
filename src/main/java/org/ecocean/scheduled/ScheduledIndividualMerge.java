package org.ecocean.scheduled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

public class ScheduledIndividualMerge extends WildbookScheduledTask {

    private static final long serialVersionUID = 1L;

    private MarkedIndividual primaryIndividual = null;
    private MarkedIndividual secondaryIndividual = null;
    // participant names, with Boolean list of denied state in position 0, ignored state in position 2.
    private HashMap<String,ArrayList<Boolean>> participantsDeniedIgnored = new HashMap<>();

    private List<String> participants = new ArrayList<>();

    public ScheduledIndividualMerge() {}

    public ScheduledIndividualMerge(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, long executionTime, String initiatorName) {
        this.scheduledTaskType = "ScheduledIndividualMerge";
        this.taskScheduledExecutionTimeLong = executionTime;
        this.primaryIndividual = primaryIndividual;
        this.secondaryIndividual = secondaryIndividual;
        this.initiatorName = initiatorName;
        this.setParticipants();
    }

    @Override
    public void execute() {
        System.out.println("[WARN]: WildbookScheduledTask.execute() was called without proper arguments for task type "+this.scheduledTaskType+". Failed.");
    }

    @Override
    public void execute(Shepherd myShepherd) {
        try {
            myShepherd.beginDBTransaction();

            // if not denied by a user !
            mergeIndividuals(primaryIndividual, secondaryIndividual, myShepherd);

            this.setTaskComplete();

        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            this.setTaskIncomplete();
            e.printStackTrace();
        } finally {
            myShepherd.updateDBTransaction();
        }
        
    }
    
    private void mergeIndividuals(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, Shepherd myShepherd) {
        if (primaryIndividual!=null&&secondaryIndividual!=null) {
            try {
                System.out.println("MergeIndividual task is within execution time. Trying to merge individuals.");
                primaryIndividual.mergeIndividual(secondaryIndividual, initiatorName);
                myShepherd.updateDBTransaction();
                MarkedIndividual tempSecondaryIndividual = secondaryIndividual;
                //avoiding a foreign key error
                this.secondaryIndividual = null;
                myShepherd.updateDBTransaction();
                myShepherd.throwAwayMarkedIndividual(tempSecondaryIndividual);
            } catch (Exception e) {
                System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with ScheduledIndividualMerge.");
                e.printStackTrace();
            }
        } else {
            System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with ScheduledIndividualMerge due to null candidate individual.");
        }
       
    }

    private void setParticipants() {
        if (primaryIndividual!=null&&secondaryIndividual!=null) {
            ArrayList<String> primaryIndividualUsernames = primaryIndividual.getAllAssignedUsers();
            ArrayList<String> secondaryIndividualUsernames = secondaryIndividual.getAllAssignedUsers();
            List<String> usernameMasterList = new ArrayList<>();
            if (primaryIndividualUsernames!=null) {
                usernameMasterList.addAll(primaryIndividualUsernames);
            }
            if (secondaryIndividualUsernames!=null) {
                usernameMasterList.addAll(secondaryIndividualUsernames);
            }
            participants.addAll(usernameMasterList);
            for (String username : usernameMasterList) {
                ArrayList<Boolean> denyIgnore = new ArrayList<>(1);
    
                //on instantiation, no one has denied the action
                denyIgnore.add(0, Boolean.FALSE);
                //or ignored it
                denyIgnore.add(1, Boolean.FALSE);
    
                participantsDeniedIgnored.put(username, denyIgnore);
            }
            System.out.println("List of Scheduled merge participants: "+Arrays.toString(participants.toArray()));
        } else {
            System.out.println("You cannot set participants on an IndividualMerge task that doesn't have two MarkedIndividuals.");
        }
    }

    public List<String> getParticipantUsernames() {
        return participants;
    }

    public void setTaskDeniedStateForUser(String username, boolean denied) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        if (stateForUser!=null) {
            stateForUser.add(0, denied);
        }
    }

    public void setTaskIgnoredStateForUser(String username, boolean ignored) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        if (stateForUser!=null) {
            stateForUser.add(1, ignored);
        }
    }

    public boolean deniedByUser(String username) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        if (stateForUser==null) return Boolean.FALSE;
        return stateForUser.get(0);
    }

    public boolean ignoredByUser(String username) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        System.out.println("merge stateForUser: "+Arrays.toString(stateForUser.toArray()));
        return stateForUser.get(1);
    }

    public String getUsernameThatDeniedMerge() {
        for (String participant : participants) {
            if (participantsDeniedIgnored.get(participant)!=null&&participantsDeniedIgnored.get(participant).get(0)) {
                return participant;
            }
        }   
        return null;
    }

    public boolean isDenied() {
        return getUsernameThatDeniedMerge()!=null;
    }

    public boolean isUserParticipent(String username) {
        return participants.contains(username);
    }

    public MarkedIndividual getPrimaryIndividual() {
        return primaryIndividual;
    }

    public MarkedIndividual getSecondaryIndividual() {
        return secondaryIndividual;
    }
    
}