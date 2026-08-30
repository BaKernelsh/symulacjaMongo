package com.sim.mongo.distributions;

import java.util.Random;

public class ExponentialDistribution implements Distribution {
    private final Random rng = new Random();
    private final double mean;

    public ExponentialDistribution(double mean) { this.mean = mean; }

    @Override
    public double sample() {
        double u = rng.nextDouble();
        return -mean * Math.log(1 - u);
    }
}
