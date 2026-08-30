package com.sim.mongo.stats;

import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

public class StatsCollector {
    private final AtomicLong totalResponses = new AtomicLong();
    private final AtomicLong totalLatency = new AtomicLong();
    private final AtomicLong totalLockWait = new AtomicLong();
    private final List<Long> latencies = new ArrayList<>();
    private final List<Long> waits = new ArrayList<>();

    public synchronized void recordResponse(long latencyMs, long waitForLocksMs) {
        totalResponses.incrementAndGet();
        totalLatency.addAndGet(latencyMs);
        totalLockWait.addAndGet(waitForLocksMs);
        latencies.add(latencyMs);
        waits.add(waitForLocksMs);
    }

    public void report() {
        long n = totalResponses.get();
        System.out.println("--- Simulation Stats ---");
        System.out.println("Responses: " + n);
        System.out.println("Avg latency ms: " + (n==0?0:((double)totalLatency.get()/n)));
        System.out.println("Avg lock wait ms: " + (n==0?0:((double)totalLockWait.get()/n)));
        System.out.println("Max latency ms: " + (latencies.stream().mapToLong(x->x).max().orElse(0)));
        System.out.println("Max wait ms: " + (waits.stream().mapToLong(x->x).max().orElse(0)));
    }
}
