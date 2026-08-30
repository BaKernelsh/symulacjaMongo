package com.sim.mongo.events;

import com.sim.mongo.Event;
import lombok.Getter;
import org.ja.Operation;

public class OperationEndEvent extends Event {
    @Getter
    private Operation operation;

    public OperationEndEvent(Operation operation, long time){
        super(time);

    }

    @Override
    public void process() {
        operation.endOperationSuccessfully(time);
    }
}
