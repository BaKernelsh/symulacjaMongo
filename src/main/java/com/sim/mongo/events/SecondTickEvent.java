package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Client;

/**
 * A per-second tick. The Client will sample the number of operations for that second and schedule them
 * at random times within the second.
 */
public class SecondTickEvent extends Event {
    private final Client client;

    public SecondTickEvent(long time, Client client) {
        super(time);
        this.client = client;
    }

    @Override
    public void process() {
        client.onSecondTick(time);
    }
}
