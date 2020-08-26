package org.ecocean.scheduled;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

public class WildbookScheduledIndividualMerge extends WildbookScheduledTask {

    private static final long serialVersionUID = 1L;

    MarkedIndividual primaryIndividual;
    MarkedIndividual secondaryIndividual;

    // maybe hold users here to inform, or just the initiator?
    String initiatorName;

    public WildbookScheduledIndividualMerge() {} //empty for jdo

    public WildbookScheduledIndividualMerge(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, long executionTime, String initiatorName) {
        this.scheduledTaskType = "WildbookScheduledIndividualMerge";
        this.taskScheduledExecutionTimeLong = executionTime;
        this.primaryIndividual = primaryIndividual;
        this.secondaryIndividual = secondaryIndividual;
        this.initiatorName = initiatorName;
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

            //TODO update user notifications

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
    
}