package com.sim.mongo.events;

import com.sim.mongo.Event;

public class ResultArrivesToSourceEvent extends Event {

    public ResultArrivesToSourceEvent(long time){
        super(time);
    }

    @Override
    public void process() {

    }
}
