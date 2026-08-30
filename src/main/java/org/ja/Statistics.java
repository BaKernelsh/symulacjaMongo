package org.ja;

import lombok.Getter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects comprehensive statistics about operations from the Source perspective:
 * - Response time distribution and percentiles
 * - Lock wait time and percentage of operations that waited
 * - Throughput (operations per second)
 * All metrics tracked both globally and per operation type
 */
public class Statistics {

    // Response time tracking (from creation to result reaching source)
    private List<Long> allResponseTimes = Collections.synchronizedList(new ArrayList<>());
    private Map<String, List<Long>> responseTimesByOperation = new ConcurrentHashMap<>();

    // Lock wait time tracking
    private Map<Operation, Long> operationLockWaitTimes = new ConcurrentHashMap<>();
    private List<Long> allLockWaitTimes = Collections.synchronizedList(new ArrayList<>());
    private Map<String, List<Long>> lockWaitTimesByOperation = new ConcurrentHashMap<>();

    // Throughput tracking (operations ended per second)
    @Getter
    private Map<Long, Integer> throughputBySecond = new ConcurrentHashMap<>(); // secondStart -> count
    @Getter
    private Map<Long, Map<String, Integer>> throughputBySecondPerOperation = new ConcurrentHashMap<>();

    @Getter
    private long totalOperationsCompleted = 0;

    private final Set<Operation> operationsThatWaitedForLocks = Collections.synchronizedSet(new HashSet<>());


    /**
     * Record an operation's lock wait time when a conflict occurs
     */
    public void recordLockWaitTime(Operation operation, long waitTimeMs) {
        long currentWait = operationLockWaitTimes.getOrDefault(operation, 0L);
        operationLockWaitTimes.put(operation, currentWait + waitTimeMs);
        allLockWaitTimes.add(waitTimeMs);

        String opType = operation.getOperationDefinition().getType().toString();
        lockWaitTimesByOperation.computeIfAbsent(opType, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(waitTimeMs);

        operationsThatWaitedForLocks.add(operation);
    }

    /**
     * Record operation completion with response time (from source perspective)
     * Response time = resultReachedSourceTime - creationTime
     */
    public void recordOperationCompletion(Operation operation, long responseTimeMs) {
        allResponseTimes.add(responseTimeMs);

        String opType = operation.getOperationDefinition().getType().toString();
        responseTimesByOperation.computeIfAbsent(opType, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(responseTimeMs);

        // Track throughput
        long secondStart = getSecondBucket(operation.getCreationTime());
        throughputBySecond.merge(secondStart, 1, Integer::sum);
        throughputBySecondPerOperation.computeIfAbsent(secondStart, k -> new ConcurrentHashMap<>())
                .merge(opType, 1, Integer::sum);

        totalOperationsCompleted++;
    }

    /**
     * Calculate response time percentiles for all operations
     * @param percentiles array of percentiles to calculate (e.g., [50, 95, 99, 99.9])
     * @return map of percentile -> response time in ms
     */
    public Map<Double, Long> getResponseTimePercentilesAllOps(double[] percentiles) {
        return calculatePercentiles(allResponseTimes, percentiles);
    }

    /**
     * Calculate response time percentiles for a specific operation type
     */
    public Map<Double, Long> getResponseTimePercentilesByOperation(String operationType, double[] percentiles) {
        List<Long> opTimes = responseTimesByOperation.getOrDefault(operationType, new ArrayList<>());
        return calculatePercentiles(opTimes, percentiles);
    }

    /**
     * Get response time statistics for all operations
     */
    public ResponseTimeStats getResponseTimeStatsAllOps() {
        return new ResponseTimeStats(allResponseTimes);
    }

    /**
     * Get response time statistics for a specific operation type
     */
    public ResponseTimeStats getResponseTimeStatsByOperation(String operationType) {
        List<Long> opTimes = responseTimesByOperation.getOrDefault(operationType, new ArrayList<>());
        return new ResponseTimeStats(opTimes);
    }

    /**
     * Get total lock wait time for all operations
     */
    public long getTotalLockWaitTimeAllOps() {
        return allLockWaitTimes.stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Get total lock wait time for a specific operation type
     */
    public long getTotalLockWaitTimeByOperation(String operationType) {
        List<Long> opTimes = lockWaitTimesByOperation.getOrDefault(operationType, new ArrayList<>());
        return opTimes.stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Get average lock wait time for all operations
     */
    public double getAverageLockWaitTimeAllOps() {
        if (allLockWaitTimes.isEmpty()) return 0.0;
        return (double) getTotalLockWaitTimeAllOps() / allLockWaitTimes.size();
    }

    /**
     * Get average lock wait time for a specific operation type
     */
    public double getAverageLockWaitTimeByOperation(String operationType) {
        List<Long> opTimes = lockWaitTimesByOperation.getOrDefault(operationType, new ArrayList<>());
        if (opTimes.isEmpty()) return 0.0;
        return (double) opTimes.stream().mapToLong(Long::longValue).sum() / opTimes.size();
    }

    /**
     * Get percentage of operations that waited for locks (all operations)
     */
    public double getPercentageOfOpsWaitedForLocksAllOps() {
        if (totalOperationsCompleted == 0) return 0.0;
        return (double) operationsThatWaitedForLocks.size() / totalOperationsCompleted * 100.0;
    }

    /**
     * Get percentage of operations that waited for locks (by operation type)
     */
    public double getPercentageOfOpsWaitedForLocksByOperation(String operationType) {
        long countByOp = responseTimesByOperation.getOrDefault(operationType, new ArrayList<>()).size();
        if (countByOp == 0) return 0.0;

        long waitedByOp = operationsThatWaitedForLocks.stream()
                .filter(op -> op.getOperationDefinition().getType().toString().equals(operationType))
                .count();

        return (double) waitedByOp / countByOp * 100.0;
    }

    /**
     * Get throughput (operations per second) for all operations
     * @return map of second (ms) -> number of operations
     */
    public Map<Long, Integer> getThroughputAllOps() {
        return new TreeMap<>(throughputBySecond);
    }

    /**
     * Get throughput for a specific operation type
     * @return map of second (ms) -> number of operations
     */
    public Map<Long, Integer> getThroughputByOperation(String operationType) {
        Map<Long, Integer> result = new TreeMap<>();
        for (Map.Entry<Long, Map<String, Integer>> entry : throughputBySecondPerOperation.entrySet()) {
            Integer count = entry.getValue().getOrDefault(operationType, 0);
            if (count > 0) {
                result.put(entry.getKey(), count);
            }
        }
        return result;
    }

    /**
     * Get average throughput (ops/sec) for all operations
     */
    public double getAverageThroughputAllOps() {
        if (throughputBySecond.isEmpty()) return 0.0;
        return (double) totalOperationsCompleted / throughputBySecond.size();
    }

    /**
     * Get average throughput (ops/sec) for a specific operation type
     */
    public double getAverageThroughputByOperation(String operationType) {
        long totalByOp = responseTimesByOperation.getOrDefault(operationType, new ArrayList<>()).size();
        long secondsWithOps = throughputBySecondPerOperation.values().stream()
                .filter(map -> map.containsKey(operationType))
                .count();
        if (secondsWithOps == 0) return 0.0;
        return (double) totalByOp / secondsWithOps;
    }

    /**
     * Get all recorded operation types
     */
    public Set<String> getRecordedOperationTypes() {
        return new HashSet<>(responseTimesByOperation.keySet());
    }

    /**
     * Helper method to calculate percentiles
     */
    private Map<Double, Long> calculatePercentiles(List<Long> values, double[] percentiles) {
        Map<Double, Long> result = new LinkedHashMap<>();
        if (values.isEmpty()) {
            for (double p : percentiles) {
                result.put(p, 0L);
            }
            return result;
        }

        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        for (double percentile : percentiles) {
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            result.put(percentile, sorted.get(index));
        }

        return result;
    }

    /**
     * Helper method to get second bucket for an operation's start time
     */
    private long getSecondBucket(long timeMs) {
        return (timeMs / 1000) * 1000;
    }

    /**
     * Inner class for response time statistics
     */
    public static class ResponseTimeStats {
        public final long min;
        public final long max;
        public final double avg;
        public final long median;
        public final long p95;
        public final long p99;
        public final long p999;
        public final long count;

        public ResponseTimeStats(List<Long> times) {
            this.count = times.size();
            if (times.isEmpty()) {
                this.min = 0;
                this.max = 0;
                this.avg = 0.0;
                this.median = 0;
                this.p95 = 0;
                this.p99 = 0;
                this.p999 = 0;
            } else {
                List<Long> sorted = new ArrayList<>(times);
                Collections.sort(sorted);

                this.min = sorted.get(0);
                this.max = sorted.get(sorted.size() - 1);
                this.avg = times.stream().mapToLong(Long::longValue).average().orElse(0.0);

                this.median = percentile(sorted, 50);
                this.p95 = percentile(sorted, 95);
                this.p99 = percentile(sorted, 99);
                this.p999 = percentile(sorted, 99.9);
            }
        }

        private long percentile(List<Long> sorted, double p) {
            int index = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }

        @Override
        public String toString() {
            return String.format(
                    "ResponseTimeStats{count=%d, min=%dms, max=%dms, avg=%.2fms, median=%dms, p95=%dms, p99=%dms, p99.9=%dms}",
                    count, min, max, avg, median, p95, p99, p999
            );
        }
    }
}
