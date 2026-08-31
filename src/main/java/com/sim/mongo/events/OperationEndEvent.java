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

            Statistics.instance().recordLockWaitTime(operation);
        }
    }

    public void cancel(){
        rescheduled = true;
    }
}
