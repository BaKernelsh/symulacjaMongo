package com.sim.mongo;

import com.sim.mongo.distributions.Distribution;
import com.sim.mongo.distributions.ExponentialDistribution;
import com.sim.mongo.distributions.ConstantDistribution;
import com.sim.mongo.model.Cluster;
import com.sim.mongo.model.Client;
import com.sim.mongo.stats.StatsCollector;

public class Simulation {
    private final Cluster cluster;
    private final EventScheduler scheduler;
    private final StatsCollector stats;

    // configurable distributions
    private Distribution interArrival; // time between client requests
    private Distribution opServiceTime; // how long an operation holds locks
    private Distribution txFanout; // number of shards in a transaction
    private int clients;
    private double readProb;
    private double txProb;
    private long simulationTimeMs;

    public Simulation(int shards) {
        this.cluster = new Cluster(shards);
        this.scheduler = new EventScheduler();
        this.stats = new StatsCollector();
        // defaults
        this.interArrival = new ExponentialDistribution(50.0); // ms
        this.opServiceTime = new ConstantDistribution(10.0);
        this.txFanout = new ConstantDistribution(2.0);
        this.clients = 10;
        this.readProb = 0.7;
        this.txProb = 0.1;
        this.simulationTimeMs = 60_000; // 1 minute
    }

    // parameter setters
    public void setInterArrival(Distribution d) { this.interArrival = d; }
    public void setOpServiceTime(Distribution d) { this.opServiceTime = d; }
    public void setTxFanout(Distribution d) { this.txFanout = d; }
    public void setClients(int c) { this.clients = c; }
    public void setReadProbability(double p) { this.readProb = p; }
    public void setTransactionProbability(double p) { this.txProb = p; }
    public void setSimulationTimeMs(long ms) { this.simulationTimeMs = ms; }

    public void run() {
        // schedule client arrivals
        for (int i = 0; i < clients; ++i) {
            Client client = new Client(i, cluster, scheduler, stats, interArrival, opServiceTime, txFanout, readProb, txProb);
            client.scheduleNext(0);
        }

        long end = simulationTimeMs;
        while (!scheduler.isEmpty()) {
            Event e = scheduler.nextEvent();
            if (e.getTime() > end) break;
            e.process();
        }

        stats.report();
    }

    public static void main(String[] args) {
        Simulation s = new Simulation(4);
        // Example: users can set distributions here or via code that uses Simulation API
        s.run();
    }
}
