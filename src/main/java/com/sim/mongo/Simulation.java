package com.sim.mongo;

import com.sim.mongo.events.NewSecondEvent;
import com.sim.mongo.model.Source;


import java.util.Map;

public class Simulation {

    private long simulationTimeMs;

    private final Map<String, Source> clients;


    public Simulation(Map<String, Source> clients) {

        this.clients = clients;
    }


    public void setSimulationTimeMs(long ms) { this.simulationTimeMs = ms; }

    public void setup(){
        //schedule seconds starts events
        for(long i=0; i<simulationTimeMs;){
            GlobalScheduler.instance().schedule(new NewSecondEvent(clients, i));
            i += 1000;
        }
    }

    public void run() {
        long end = simulationTimeMs;
        while (!GlobalScheduler.instance().isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                // allow interrupt-based cancellation
                break;
            }
            Event e = GlobalScheduler.instance().next();
            if (e == null) break;
            if (e.getTime() > end) break;
            e.process();
        }
    }

    public static void main(String[] args) {

    }
}
