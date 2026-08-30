package com.sim.mongo;

public abstract class Event implements Comparable<Event> {
    protected final long time;

    public Event(long time) { this.time = time; }
    public long getTime() { return time; }
    public abstract void process();

    @Override
    public int compareTo(Event o) {
        return Long.compare(this.time, o.time);
    }
}
