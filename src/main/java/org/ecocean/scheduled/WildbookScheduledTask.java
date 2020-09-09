package org.ecocean.scheduled;

import java.time.*;
import java.time.format.DateTimeFormatter;

import org.ecocean.Shepherd;
import org.ecocean.Util;

public abstract class WildbookScheduledTask implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    protected String id = Util.generateUUID();

    protected boolean taskComplete = false;
    protected long taskCreatedLong;
    protected long taskScheduledExecutionTimeLong;
    protected String scheduledTaskType = "WildbookScheduledTask";
    protected String initiatorName = null;

    public WildbookScheduledTask() {} //empty for jdo

    public String getId() {
        return id;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public long getTaskCreatedLong() {
      return taskCreatedLong;
    }

    public long getTaskScheduledExecutionTimeLong() {
        return taskScheduledExecutionTimeLong;
    }

    public String getTaskScheduledExecutionDateString() {
        Instant instant = Instant.ofEpochMilli(getTaskScheduledExecutionTimeLong());
        LocalDateTime executionDate = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return dtf.format(executionDate);
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
    public void execute() {
        System.out.println("[WARN]: WildbookScheduledTask.execute() was called with no defined execution strategy for task type "+this.scheduledTaskType+". Failed.");
    }
    
    //Override in inheriting class
    public void execute(Shepherd myShepherd) {
        execute();
    }

}