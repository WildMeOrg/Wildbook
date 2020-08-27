package org.ecocean.scheduled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.xpath.operations.Bool;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

public class WildbookScheduledIndividualMerge extends WildbookScheduledTask {

    private static final long serialVersionUID = 1L;

    private MarkedIndividual primaryIndividual;
    private MarkedIndividual secondaryIndividual;

    private String initiatorName;

    // participant names, with Boolean list of denied state in position 0, ignored state in position 2.
    private HashMap<String,List<Boolean>> participantsDeniedIgnored = new HashMap<String,List<Boolean>>();

    public WildbookScheduledIndividualMerge() {} //empty for jdo

    public WildbookScheduledIndividualMerge(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, long executionTime, String initiatorName) {
        this.scheduledTaskType = "WildbookScheduledIndividualMerge";
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
                System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with WildbookScheduledIndividualMerge.");
                e.printStackTrace();
            }
        } else {
            System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with WildbookScheduledIndividualMerge due to null candidate individual.");
        }
       
    }

    // this stuff could probably be moved to another abstract class for tasks requiring user confimation or denial
    public void setParticipants() {
        if (primaryIndividual==null||secondaryIndividual==null) {
            ArrayList<String> primaryIndividualUsernames = primaryIndividual.getAllAssignedUsers();
            ArrayList<String> secondaryIndividualUsernames = secondaryIndividual.getAllAssignedUsers();
            List<String> usernameMasterList = new ArrayList<>();
            if (primaryIndividualUsernames!=null) {
                usernameMasterList.addAll(primaryIndividualUsernames);
            }
            if (secondaryIndividualUsernames!=null) {
                usernameMasterList.addAll(secondaryIndividualUsernames);
            }
            for (String username : usernameMasterList) {
                List<Boolean> denyIgnore = new ArrayList<>(1);
    
                //on instantiation, no one has denied the action
                denyIgnore.add(0, Boolean.FALSE);
                //or ignored it
                denyIgnore.add(1, Boolean.FALSE);
    
                participantsDeniedIgnored.put(username, denyIgnore);
            }
            System.out.println("List of Scheduled merge participants: ");
        } else {
            System.out.println("You cannot set participants on an IndividualMerge task that doesn't have two MarkedIndividuals.");
        }
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

    public Boolean getTaskDeniedStateForUser(String username) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        if (stateForUser==null) return Boolean.FALSE;
        return stateForUser.get(0);
    }

    public Boolean getTaskIgnoredStateForUser(String username) {
        List<Boolean> stateForUser = participantsDeniedIgnored.get(username);
        if (stateForUser==null) return Boolean.FALSE;
        return stateForUser.get(1);
    }
    
}