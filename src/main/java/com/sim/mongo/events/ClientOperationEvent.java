package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Client;

/**
 * A scheduled client operation (one of the operations that a client decided to issue during a second)
 */
public class ClientOperationEvent extends Event {
    private final Client client;

    public ClientOperationEvent(long time, Client client) {
        super(time);
        this.client = client;
    }

    @Override
    public void process() {
        client.onRequest(time);
    }
}
