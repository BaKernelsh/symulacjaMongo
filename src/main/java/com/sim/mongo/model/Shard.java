package com.sim.mongo.model;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A very simple shard lock model. Supports exclusive locks only for simplicity but also tracks read vs write
 * by allowing multiple concurrent readers; writers are exclusive.
 */
public class Shard {
    private final int id;
    private int readers = 0;
    private boolean writer = false;

    public Shard(int id) { this.id = id; }

    /** Try to acquire shared (read) lock immediately. */
    public synchronized boolean tryAcquireShared() {
        if (writer) return false;
        readers++;
        return true;
    }

    public synchronized void releaseShared() {
        if (readers <= 0) throw new IllegalStateException("No reader to release");
        readers--;
    }

    public synchronized boolean tryAcquireExclusive() {
        if (writer || readers > 0) return false;
        writer = true;
        return true;
    }

    public synchronized void releaseExclusive() {
        if (!writer) throw new IllegalStateException("No writer to release");
        writer = false;
    }

    public int getId() { return id; }
}
