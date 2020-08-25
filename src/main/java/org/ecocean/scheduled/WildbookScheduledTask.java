package org.ecocean.scheduled;

import org.ecocean.Shepherd;
import org.ecocean.Util;

public abstract class WildbookScheduledTask {

    final String id = Util.generateUUID();
    boolean taskComplete = false;
    long taskCreatedLong;
    long taskScheduledExecutionTimeLong;
    String scheduledTaskType;

    public WildbookScheduledTask() {} //empty for jdo

    public long getTaskCreatedLong() {
      return taskCreatedLong;
    }

    public long getTaskScheduledExecutionTomeLong() {
        return taskScheduledExecutionTimeLong;
    }

    public boolean isTaskComplete() {
        return taskComplete;
    }

    public void setTaskComplete() {
        this.taskComplete = true;
    }

    public void setTaskIncomplete() {
        this.taskComplete = false;
    }

    public boolean isTaskEligibleForExecution() {
        final long currentTime = System.currentTimeMillis();
        return currentTime > taskScheduledExecutionTimeLong;
    }
    
    //Override in inheriting class
    // some future types may not need shepherd
    public void execute() {
        System.out.println("[WARN]: WildbookScheduledTask.execute() was called with no defined execution strategy for task type "+this.scheduledTaskType+". Failed.");
    }
    
    //Override in inheriting class
    public void execute(Shepherd myShepherd) {
        execute();
    }



}