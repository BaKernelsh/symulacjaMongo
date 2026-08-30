package com.sim.mongo.model;

import com.sim.mongo.stats.StatsCollector;

import java.util.List;

public class Operation {
    public enum Type { READ, WRITE, TRANSACTION }
    private final Client client;
    private final List<Integer> shards;
    private final Type type;
    private final long submitTime;
    private final double durationMs;
    private long lockAcquiredTime = -1;

    public Operation(Client client, List<Integer> shards, Type type, long submitTime, double durationMs) {
        this.client = client;
        this.shards = shards;
        this.type = type;
        this.submitTime = submitTime;
        this.durationMs = durationMs;
    }

    public Client getClient() { return client; }
    public List<Integer> getShards() { return shards; }
    public Type getType() { return type; }
    public long getSubmitTime() { return submitTime; }
    public double getDurationMs() { return durationMs; }

    public void setLockAcquiredTime(long t) { lockAcquiredTime = t; }
    public long getLockAcquiredTime() { return lockAcquiredTime; }
}
