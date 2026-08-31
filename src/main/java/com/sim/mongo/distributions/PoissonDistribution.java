package com.sim.mongo.distributions;

public class PoissonDistribution implements Distribution{

    private double lambda;

    public PoissonDistribution(double lambda){
        this.lambda = lambda;
    }


    public double sample(){
        if (lambda <= 0) {
            return 0;
        }

        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= Math.random();
        } while (p > L);

        return k - 1;
    }

}
