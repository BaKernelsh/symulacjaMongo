package com.sim.mongo.model;

import com.sim.mongo.EventScheduler;
import com.sim.mongo.stats.StatsCollector;
import com.sim.mongo.distributions.Distribution;
import com.sim.mongo.events.ClientArrivalEvent;

public class Client {
    private final int id;
    private final Cluster cluster;
    private final EventScheduler scheduler;
    private final StatsCollector stats;
    private final Distribution interArrival;
    private final Distribution opService;
    private final Distribution txFanout;
    private final double readProb;
    private final double txProb;

    public Client(int id, Cluster cluster, EventScheduler scheduler, StatsCollector stats,
                  Distribution interArrival, Distribution opService, Distribution txFanout,
                  double readProb, double txProb) {
        this.id = id;
        this.cluster = cluster;
        this.scheduler = scheduler;
        this.stats = stats;
        this.interArrival = interArrival;
        this.opService = opService;
        this.txFanout = txFanout;
        this.readProb = readProb;
        this.txProb = txProb;
    }

    public void scheduleNext(long now) {
        long delay = (long) Math.max(1, interArrival.sample());
        scheduler.schedule(new ClientArrivalEvent(now + delay, this));
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
        // schedule next arrival
        scheduleNext(now);
    }

    public void recordResponse(long submitTime, long completionTime, long waitingForLocks) {
        stats.recordResponse(completionTime - submitTime, waitingForLocks);
    }

    public int getId() { return id; }
}
