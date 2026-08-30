package com.sim.mongo.distributions;

public class ConstantDistribution implements Distribution {
    private final double value;
    public ConstantDistribution(double value) { this.value = value; }
    @Override
    public double sample() { return value; }
}
