package com.sim.mongo.events;

import com.sim.mongo.Event;
import org.ja.Operation;

public class OperationArrivesToNodeEvent extends Event {

    private Operation operation;

    public OperationArrivesToNodeEvent(Operation operation, long time){
        super(time);
        this.operation = operation;
    }

    @Override
    public void process() {
        System.out.println("process operation arrives at node event");
        operation.arriveOperationToShard(time);
    }
}
