package com.sim.mongo;

import com.sim.mongo.distributions.Distribution;
import com.sim.mongo.distributions.ExponentialDistribution;
import com.sim.mongo.distributions.ConstantDistribution;
import com.sim.mongo.model.Cluster;
import com.sim.mongo.model.Client;
import com.sim.mongo.stats.StatsCollector;

public class Simulation {
    private final Cluster cluster;
    private final StatsCollector stats;

    // configurable distributions
    private Distribution opsPerSecond; // number of operations per second per client
    private Distribution opServiceTime; // how long an operation holds locks
    private Distribution txFanout; // number of shards in a transaction
    private int clients;
    private double readProb;
    private double txProb;
    private long simulationTimeMs;

    public Simulation(int shards) {
        this.cluster = new Cluster(shards);
        this.stats = new StatsCollector();
        // defaults
        this.opsPerSecond = new ExponentialDistribution(1.0); // mean 1 op/sec per client
        this.opServiceTime = new ConstantDistribution(10.0);
        this.txFanout = new ConstantDistribution(2.0);
        this.clients = 10;
        this.readProb = 0.7;
        this.txProb = 0.1;
        this.simulationTimeMs = 60_000; // 1 minute
    }

    // parameter setters
    public void setOpsPerSecond(Distribution d) { this.opsPerSecond = d; }
    public void setOpServiceTime(Distribution d) { this.opServiceTime = d; }
    public void setTxFanout(Distribution d) { this.txFanout = d; }
    public void setClients(int c) { this.clients = c; }
    public void setReadProbability(double p) { this.readProb = p; }
    public void setTransactionProbability(double p) { this.txProb = p; }
    public void setSimulationTimeMs(long ms) { this.simulationTimeMs = ms; }

    public void run() {
        // schedule client per-second ticks
        for (int i = 0; i < clients; ++i) {
            Client client = new Client(i, cluster, stats, opsPerSecond, opServiceTime, txFanout, readProb, txProb);
            // schedule first tick at t=0
            GlobalScheduler.instance().schedule(new com.sim.mongo.events.SecondTickEvent(0, client));
        }

        long end = simulationTimeMs;
        while (!GlobalScheduler.instance().isEmpty()) {
            Event e = GlobalScheduler.instance().next();
            if (e == null) break;
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
