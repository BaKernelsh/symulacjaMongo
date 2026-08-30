package com.sim.mongo;

import com.sim.mongo.distributions.Distribution;
import com.sim.mongo.distributions.ExponentialDistribution;
import com.sim.mongo.distributions.ConstantDistribution;
import com.sim.mongo.model.Cluster;
import com.sim.mongo.model.Client;
import com.sim.mongo.stats.StatsCollector;

import java.util.function.Supplier;

public class Simulation {
    private final Cluster cluster;
    private final StatsCollector stats;

    // configurable distributions
    // opsPerSecondSupplier produces a fresh Distribution instance per client so each client samples independently
    private Supplier<Distribution> opsPerSecondSupplier; // number of operations per second per client (mean represented by distribution)
    private Distribution opServiceTime; // how long an operation holds locks
    private Distribution txFanout; // number of shards in a transaction
    private int clients;
    private double readProb;
    private double txProb;
    private long simulationTimeMs;

    public Simulation(int shards) {
        this.cluster = new Cluster(shards);
        this.stats = new StatsCollector();
        // defaults: supplier returns a new ExponentialDistribution per client (mean 1 op/sec)
        this.opsPerSecondSupplier = () -> new ExponentialDistribution(1.0);
        this.opServiceTime = new ConstantDistribution(10.0);
        this.txFanout = new ConstantDistribution(2.0);
        this.clients = 10;
        this.readProb = 0.7;
        this.txProb = 0.1;
        this.simulationTimeMs = 60_000; // 1 minute
    }

    // parameter setters
    // provide a supplier so each client gets an independent Distribution instance
    public void setOpsPerSecondSupplier(Supplier<Distribution> supplier) { this.opsPerSecondSupplier = supplier; }
    public void setOpServiceTime(Distribution d) { this.opServiceTime = d; }
    public void setTxFanout(Distribution d) { this.txFanout = d; }
    public void setClients(int c) { this.clients = c; }
    public void setReadProbability(double p) { this.readProb = p; }
    public void setTransactionProbability(double p) { this.txProb = p; }
    public void setSimulationTimeMs(long ms) { this.simulationTimeMs = ms; }

    public void run() {
        // schedule client per-second ticks
        for (int i = 0; i < clients; ++i) {
            // create independent distribution instance for this client
            Distribution perClientOps = opsPerSecondSupplier.get();
            Client client = new Client(i, cluster, stats, perClientOps, opServiceTime, txFanout, readProb, txProb);
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
        // Example: set mean ops/sec per client to 2 using Exponential distribution (each client gets its own distribution)
        s.setOpsPerSecondSupplier(() -> new ExponentialDistribution(2.0));
        s.setClients(20);
        s.run();
    }
}
