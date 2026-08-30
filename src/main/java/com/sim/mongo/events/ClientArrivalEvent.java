package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Client;

public class ClientArrivalEvent extends Event {
    private final Client client;

    public ClientArrivalEvent(long time, Client client) {
        super(time);
        this.client = client;
    }

    @Override
    public void process() {
        client.onRequest(time);
    }
}
