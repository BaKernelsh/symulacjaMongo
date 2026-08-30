package com.sim.mongo.distributions;

import java.util.Random;

public class UniformDistribution implements Distribution {
    private final Random rng = new Random();
    private final double a, b;
    public UniformDistribution(double a, double b) { this.a = a; this.b = b; }
    @Override
    public double sample() { return a + (b - a) * rng.nextDouble(); }
}
