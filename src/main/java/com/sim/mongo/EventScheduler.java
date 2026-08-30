package com.sim.mongo;

import java.util.PriorityQueue;

public class EventScheduler {
    private final PriorityQueue<Event> queue = new PriorityQueue<>();

    public void schedule(Event e) { queue.add(e); }
    public Event nextEvent() { return queue.poll(); }
    public boolean isEmpty() { return queue.isEmpty(); }
}
