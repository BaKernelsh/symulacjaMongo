package com.sim.mongo.events;

import com.sim.mongo.Event;
import lombok.Getter;
import org.ja.Operation;
import org.ja.statistics.Statistics;

public class OperationEndEvent extends Event {
    @Getter
    private Operation operation;
    private boolean rescheduled = false;

    public OperationEndEvent(Operation operation, long time){
        super(time);
        this.operation = operation;
    }

    @Override
    public void process() {
        if(!rescheduled) {
            operation.endOperationSuccessfully(time);

            System.out.println("operation end event - adding lock time: " + Long.toString(operation.getEndTime() - (operation.getExecutionStartTime() + operation.getBaseExecutionTimeMs())));
            Statistics.instance().recordLockWaitTime(operation);
            System.out.println("operation end event - adding response time: " + Long.toString(operation.getResultReachedSourceTime() - operation.getCreationTime()));
            Statistics.instance().recordOperationCompletion(operation);
        }
    }

    public void cancel(){
        rescheduled = true;
    }
}
