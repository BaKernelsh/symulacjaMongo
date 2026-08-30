package com.sim.mongo.model;

import com.sim.mongo.EventScheduler;
import com.sim.mongo.Event;
import com.sim.mongo.events.TryLockEvent;
import com.sim.mongo.events.OperationCompleteEvent;
import com.sim.mongo.stats.StatsCollector;

import java.util.*;

public class Cluster {
    private final List<Shard> shards;
    private final EventScheduler scheduler;
    private final StatsCollector stats;

    public Cluster(int numShards) {
        this.shards = new ArrayList<>();
        for (int i = 0; i < numShards; ++i) shards.add(new Shard(i));
        this.scheduler = new EventScheduler();
        this.stats = new StatsCollector();
    }

    // For integration with Client we accept scheduler and stats instances from outside in Client
    // But to keep simple, we provide wrapper methods used by Client that use a global scheduler

    public void submitOperation(Client client, long now, boolean read, double durationMs) {
        // pick a shard randomly for single-shard ops
        int shardId = (int)(Math.random() * shards.size());
        Operation op = new Operation(client, Collections.singletonList(shardId), read ? Operation.Type.READ : Operation.Type.WRITE, now, durationMs);
        // schedule try-lock event immediately in the scheduler associated with Cluster
        // We'll use a shared scheduler via the first shard's scheduler; to avoid coupling, we'll use a global static
        GlobalScheduler.instance().schedule(new TryLockEvent(now, op, this));
    }

    public void submitTransaction(Client client, long now, int fanout, double durationMs) {
        // choose `fanout` distinct shards
        Set<Integer> chos = new HashSet<>();
        fanout = Math.min(fanout, shards.size());
        while (chos.size() < fanout) {
            chos.add((int)(Math.random() * shards.size()));
        }
        List<Integer> target = new ArrayList<>(chos);
        // transactions are writes for simplicity
        Operation tx = new Operation(client, target, Operation.Type.TRANSACTION, now, durationMs);
        GlobalScheduler.instance().schedule(new TryLockEvent(now, tx, this));
    }

    public Shard getShard(int id) { return shards.get(id); }

    public List<Shard> getShards() { return shards; }
}
