package org.ecocean.scheduled;

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
        System.out.println("[WARN]: WildbookScheduledTask.execute() was called with no shepherd for task type "+this.scheduledTaskType+". Failed.");
    }

    @Override
    public void execute(Shepherd myShepherd) {
        // merge the individuals
        try {
            myShepherd.beginDBTransaction();

            mergeIndividuals(primaryIndividual, secondaryIndividual) ;
            
            myShepherd.updateDBTransaction();
            this.setTaskComplete();
        } catch (Exception e) {
            this.setTaskIncomplete();
            e.printStackTrace();
        }
        
    }
    
    private void mergeIndividuals(MarkedIndividual primaryIndividual, MarkedIndividual secondaryIndividual) {
        // yep, just merge em. permission should have been established already when scheduled.
    }
    
}