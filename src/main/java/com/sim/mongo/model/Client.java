package com.sim.mongo.model;

import com.sim.mongo.EventScheduler;
import com.sim.mongo.stats.StatsCollector;
import com.sim.mongo.distributions.Distribution;
import com.sim.mongo.events.ClientArrivalEvent;

public class Client {
    private final int id;
    private final Cluster cluster;
    private final StatsCollector stats;
    private final Distribution opsPerSecond;
    private final Distribution opService;
    private final Distribution txFanout;
    private final double readProb;
    private final double txProb;

    public Client(int id, Cluster cluster, StatsCollector stats,
                  Distribution opsPerSecond, Distribution opService, Distribution txFanout,
                  double readProb, double txProb) {
        this.id = id;
        this.cluster = cluster;
        this.stats = stats;
        this.opsPerSecond = opsPerSecond;
        this.opService = opService;
        this.txFanout = txFanout;
        this.readProb = readProb;
        this.txProb = txProb;
    }

    /**
     * Called by the per-second tick scheduler. Schedules a random number of operations (sampled from
     * opsPerSecond) at random times within the current second.
     */
    public void onSecondTick(long secondStartMs) {
        int nops = Math.max(0, (int)Math.round(opsPerSecond.sample()));
        for (int i = 0; i < nops; ++i) {
            long offset = (long) (Math.random() * 1000.0); // random time within the second
            long eventTime = secondStartMs + offset;
            com.sim.mongo.GlobalScheduler.instance().schedule(new com.sim.mongo.events.ClientOperationEvent(eventTime, this));
        }
        // schedule next second tick
        com.sim.mongo.GlobalScheduler.instance().schedule(new com.sim.mongo.events.SecondTickEvent(secondStartMs + 1000, this));
    }

    public void onRequest(long now) {
        // decide operation type
        double p = Math.random();
        if (p < txProb) {
            // transaction spanning multiple shards
            int fanout = Math.max(1, (int)Math.round(txFanout.sample()));
            cluster.submitTransaction(this, now, fanout, opService.sample());
        } else {
            boolean read = Math.random() < readProb;
            cluster.submitOperation(this, now, read, opService.sample());
        }
    }

    public void recordResponse(long submitTime, long completionTime, long waitingForLocks) {
        stats.recordResponse(completionTime - submitTime, waitingForLocks);
    }

    public int getId() { return id; }
}
