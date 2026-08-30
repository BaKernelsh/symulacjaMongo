package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Source;

import java.util.Map;

public class NewSecondEvent extends Event {
    private final Map<String, Source> clients;
    private final long secondStartMs;

    public NewSecondEvent(Map<String, Source> clients, long secondStartMs){
        super(secondStartMs);
        this.clients = clients;
        this.secondStartMs = secondStartMs;
    }

    public void process(){
        clients.forEach((id, client) -> {
            client.scheduleThisSecondOperations(secondStartMs);
        });
    }
}
