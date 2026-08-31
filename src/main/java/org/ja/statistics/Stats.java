package org.ja.statistics;

import lombok.Getter;

import java.util.List;

@Getter
public class Stats {

    private double mean;
    private long p50;
    private long p95;
    private long p99;
    private long p99point9;

    public Stats(List<Long> sortedValues){

        mean = sortedValues.stream().mapToLong(Long::longValue).average().orElse(0.0);
        p50 = percentile(sortedValues, 50);
        p95 = percentile(sortedValues, 95);
        p99 = percentile(sortedValues, 99);
        p99point9 = percentile(sortedValues, 99.9);

    }

    private long percentile(List<Long> sortedValues, double p) {
        int index = (int) Math.ceil((p / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

}
