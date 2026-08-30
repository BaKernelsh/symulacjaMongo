package com.sim.mongo;

import com.sim.mongo.events.OperationEndEvent;
import org.ja.Operation;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A global scheduler used across the simple example. This bridges the small design where several
 * components need a shared scheduler. For real usage, wire scheduler explicitly.
 */
public class GlobalScheduler {
    private static final AtomicReference<GlobalScheduler> inst = new AtomicReference<>();
    private final PriorityQueue<Event> queue = new PriorityQueue<>();

    private GlobalScheduler() {}

    public static synchronized GlobalScheduler instance() {
        if (inst.get() == null) inst.set(new GlobalScheduler());
        return inst.get();
    }

    public synchronized void schedule(Event e) { queue.add(e); }
    public synchronized Event next() { return queue.poll(); }
    public synchronized boolean isEmpty() { return queue.isEmpty(); }

    public synchronized void rescheduleOperationEndEvent(Operation operation, OperationEndEvent newEvent){
        for(Event event : queue){
            if(event instanceof OperationEndEvent && ((OperationEndEvent) event).getOperation() == operation){
                queue.remove(event);
                schedule(newEvent);
                return;
            }
        }
    }

}
