package org.ecocean.scheduled;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

public class WildbookScheduledIndividualMerge extends WildbookScheduledTask {

    MarkedIndividual primaryIndividual;
    MarkedIndividual secondaryIndividual;

    // maybe hold users here to inform, or just the initiator?

    public WildbookScheduledIndividualMerge(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, long executionTime) {
        this.scheduledTaskType = "IndividualMerge";
        this.taskScheduledExecutionTimeLong = executionTime;
        this.primaryIndividual = primaryIndividual;
        this.secondaryIndividual = secondaryIndividual;
    }

    @Override
    public void execute() {
        System.out.println("[WARN]: WildbookScheduledTask.execute() was called without proper arguments for task type "+this.scheduledTaskType+". Failed.");
    }

    @Override 
    public void execute(Shepherd myShepherd) {
        execute();
    }

    @Override
    public void execute(Shepherd myShepherd, HttpServletRequest request) {
        try {
            myShepherd.beginDBTransaction();

            mergeIndividuals(primaryIndividual, secondaryIndividual, myShepherd, request);
            this.setTaskComplete();

            //update user notifications

        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            this.setTaskIncomplete();
            e.printStackTrace();
        } finally {
            myShepherd.updateDBTransaction();
        }
        
    }
    
    private void mergeIndividuals(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual, Shepherd myShepherd, HttpServletRequest request) {
        if (primaryIndividual!=null&&secondaryIndividual!=null) {
            try {
                primaryIndividual.mergeAndThrowawayIndividual(secondaryIndividual, request, myShepherd);
                myShepherd.updateDBTransaction();
                myShepherd.throwAwayMarkedIndividual(secondaryIndividual);
            } catch (Exception e) {
                System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with WildbookScheduledIndividualMerge.");
                e.printStackTrace();
            }
        } else {
            System.out.println("[ERROR]: Could not perform automatic mergeIndividuals action with WildbookScheduledIndividualMerge due to null candidate individual.");
        }
       
    }
    
}