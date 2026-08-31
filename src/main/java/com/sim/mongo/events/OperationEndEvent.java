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
            //System.out.println("operation end event - " + operation.getType());
            operation.endOperationSuccessfully(time);

            //System.out.println("operation end event - adding lock time: " + Long.toString(operation.getExecutionPlusLocksTime() - operation.getBaseExecutionTimeMs()));
            Statistics.instance().recordLockWaitTime(operation);
            //System.out.println("operation.getCreationTime() = " + operation.getCreationTime());
            //System.out.println("operation.getResultReachedSourceTime() = " +operation.getResultReachedSourceTime());
            //System.out.println("operation end event - adding response time: " + Long.toString(operation.getResultReachedSourceTime() - operation.getCreationTime()));
            Statistics.instance().recordOperationCompletion(operation);
        }
        else{
            System.out.println("processin cancelled");
        }
    }

    public void cancel(){
        rescheduled = true;
    }
}
