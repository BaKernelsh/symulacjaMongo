package com.sim.mongo;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;


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

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized int size(){
        return queue.size();
    }

    public synchronized void printEvents(){
        PriorityQueue<Event> copy = new PriorityQueue<>(queue);

        while (!copy.isEmpty()) {
            System.out.println(copy.poll());
        }
    }
}
