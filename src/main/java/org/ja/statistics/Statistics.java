package org.ja.statistics;

import lombok.Getter;
import org.ja.Operation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static Statistics instance;

    public static synchronized Statistics instance(){
        if(instance==null) instance = new Statistics();
        return instance;
    }


    private Locking lockingStatistics = new Locking();

    public void recordLockWaitTime(Operation operation) {
        lockingStatistics.recordLockWaitTime(operation);
    }

    public long getTotalLockWaitTimeAllOps() {
        return lockingStatistics.getTotalLockWaitTimeAllOps();
    }

    public Stats getLockWaitTimeStatsAllOps() {
        return lockingStatistics.getLockWaitTimeStatsAllOps();
    }

    public Map<String, Stats> getLockWaitTimeStatsByOperation() {
        return lockingStatistics.getLockWaitTimeStatsByOperation();
    }

    public double getPercentageOfOpsWaitedForLocksAllOps() {
        return lockingStatistics.getPercentageOfOpsWaitedForLocksAllOps(allResponseTimes.size());
    }

    public Map<String, Double> getPercentageOfOpsWaitedForLocksByOperation() {
        calcOperationCounts();
        return lockingStatistics.getPercentageOfOpsWaitedForLocksByOperation(opIDToCount);
    }

    Map<String, Integer> opIDToCount;
    private void calcOperationCounts(){
        if(opIDToCount==null) {
            opIDToCount = new HashMap<>();
            responseTimesByOperation.forEach((opID, respTimes) -> {
                opIDToCount.put(opID, respTimes.size());
            });
        }
    }


    public void clear(){
        lockingStatistics = new Locking();
        allResponseTimes = Collections.synchronizedList(new ArrayList<>());
        responseTimesByOperation = new ConcurrentHashMap<>();
        throughputBySecond = new ConcurrentHashMap<>();
        throughputBySecondPerOperation = new ConcurrentHashMap<>();
        totalOperationsCompleted = 0;
    }


    // Response time tracking (from creation to result reaching source)
    private List<Long> allResponseTimes = Collections.synchronizedList(new ArrayList<>());
    private Map<String, List<Long>> responseTimesByOperation = new ConcurrentHashMap<>();


    // Throughput tracking (operations ended per second)
    @Getter
    private Map<Long, Integer> throughputBySecond = new ConcurrentHashMap<>(); // secondStart -> count
    @Getter
    private Map<Long, Map<String, Integer>> throughputBySecondPerOperation = new ConcurrentHashMap<>();

    @Getter
    private long totalOperationsCompleted = 0;


    /**
     * Record operation completion with response time (from source perspective)
     * Response time = resultReachedSourceTime - creationTime
     */
    public void recordOperationCompletion(Operation operation) {
        long responseTimeMs = operation.getResultReachedSourceTime() - operation.getCreationTime();
        allResponseTimes.add(responseTimeMs);


        responseTimesByOperation.computeIfAbsent(operation.getID(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(responseTimeMs);

        // Track throughput
        long secondEnd = getSecondBucket(operation.getResultReachedSourceTime());
        throughputBySecond.merge(secondEnd, 1, Integer::sum);
        throughputBySecondPerOperation.computeIfAbsent(secondEnd, k -> new ConcurrentHashMap<>())
                .merge(operation.getID(), 1, Integer::sum);

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
    public Set<String> getRecordedOperationIDs() {
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



    /**
     * Export all collected statistics to CSV files and generate charts using Python.
     *
     * @param outputDirectory directory where CSV files and charts will be created
     * @param pythonScript path to the Python chart-generation script
     */
    public void saveResultsAndCreateCharts(String outputDirectory, String pythonScript)
            throws IOException, InterruptedException {

        Path outputDir = Paths.get(outputDirectory);
        Files.createDirectories(outputDir);

        exportResponseTimes(outputDir);
        exportLockWaiting(outputDir);
        exportThroughput(outputDir);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "python",
                pythonScript,
                outputDir.toAbsolutePath().toString()
        );

        processBuilder.inheritIO();

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "Python chart generation failed with exit code " + exitCode
            );
        }
    }

    public void saveResults(String outputDirectory)
            throws IOException, InterruptedException {

        saveResultsAndCreateCharts(
                outputDirectory,
                "statistics_charts.py"
        );
    }


    /**
     * Export response times.
     *
     * File format:
     * operation_id,response_time_ms
     */
    private void exportResponseTimes(Path outputDir) throws IOException {

        Path file = outputDir.resolve("response_times.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {

            writer.write("operation_id,response_time_ms");
            writer.newLine();

            synchronized (allResponseTimes) {
                for (Long responseTime : allResponseTimes) {
                    writer.write("ALL," + responseTime);
                    writer.newLine();
                }
            }

            responseTimesByOperation.forEach((operationId, times) -> {
                synchronized (times) {
                    for (Long responseTime : times) {
                        try {
                            writer.write(
                                    escapeCsv(operationId) + "," + responseTime
                            );
                            writer.newLine();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        }
    }


    /**
     * Export lock waiting statistics.
     *
     * File format:
     * operation_id,total_wait_ms,wait_count,operation_count,percentage_waited
     */
    private void exportLockWaiting(Path outputDir) throws IOException {

        Path file = outputDir.resolve("lock_waiting.csv");

        Map<String, Integer> operationCounts = new HashMap<>();

        responseTimesByOperation.forEach((operationId, times) -> {
            synchronized (times) {
                operationCounts.put(operationId, times.size());
            }
        });


        try (BufferedWriter writer = Files.newBufferedWriter(file)) {

            writer.write(
                    "operation_id,total_wait_ms,wait_count," +
                            "operation_count,percentage_waited"
            );
            writer.newLine();

            // All operations
            long totalWaitTime = lockingStatistics.getTotalLockWaitTimeAllOps();
            long waitedCount = lockingStatistics.getTotalWaitedCount();

            long totalOperations = allResponseTimes.size();

            double percentage = lockingStatistics.getPercentageOfOpsWaitedForLocksAllOps(allResponseTimes.size());

            writer.write(
                    "ALL,"
                            + totalWaitTime + ","
                            + waitedCount + ","
                            + totalOperations + ","
                            + percentage
            );
            writer.newLine();

            calcOperationCounts();
            System.out.println("starting saving locktime by op");
            lockingStatistics.getPercentageOfOpsWaitedForLocksByOperation(opIDToCount).forEach((opID, lockedPercentage) -> {
                System.out.println("saving lock time for " + opID);
                try {
                    writer.write(
                            escapeCsv(opID)
                                    + ","
                                    + " "
                                    + ","
                                    + lockingStatistics.getOpsWaitedForLockCountForOpID(opID)
                                    + ","
                                    + responseTimesByOperation.get(opID).size()
                                    + ","
                                    + lockedPercentage
                    );
                    writer.newLine();
                }catch (Exception e){
                    e.printStackTrace();
                }
            });

/*            // Per operation
            for (Map.Entry<String, Stats> entry : lockStats.entrySet()) {

                String operationId = entry.getKey();
                Stats stats = entry.getValue();

                int operationCount =
                        operationCounts.getOrDefault(operationId, 0);

                double operationPercentage =
                        operationCount == 0
                                ? 0.0
                                : ((double) stats.count / operationCount) * 100.0;

                writer.write(
                        escapeCsv(operationId)
                                + ","
                                + stats.total
                                + ","
                                + stats.count
                                + ","
                                + operationCount
                                + ","
                                + operationPercentage
                );

                writer.newLine();
            }*/
        }
    }


    /**
     * Export throughput.
     *
     * File format:
     * timestamp_ms,operation_id,count
     */
    private void exportThroughput(Path outputDir) throws IOException {

        Path fileAll = outputDir.resolve("throughputAll.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(fileAll)) {

            writer.write("second,count");
            writer.newLine();

            throughputBySecond.forEach((second, opCount) -> {
                try {
                    writer.write(
                            second
                                    + "," + opCount
                    );
                    writer.newLine();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        Path fileByOp = outputDir.resolve("throughputByOp.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(fileByOp)) {

            Set<String> opNames = throughputBySecondPerOperation.get(throughputBySecondPerOperation.keySet().stream().findAny().get()).keySet();
            String header = "second," + String.join(",", opNames);
            writer.write(header);
            writer.newLine();

            throughputBySecondPerOperation.forEach((second, opToCountMap) -> {
                String row = second + "," + String.join(
                        ",",
                        opToCountMap.values().stream()
                                .map(String::valueOf)
                                .toList()
                );
                try {
                    writer.write(row);
                    writer.newLine();

                }catch (Exception e){
                    e.printStackTrace();
                }
            });


/*            Map<Long, Map<String, Integer>> perOperation =
                    new TreeMap<>(throughputBySecondPerOperation);

            for (Map.Entry<Long, Map<String, Integer>> secondEntry
                    : perOperation.entrySet()) {

                long timestamp = secondEntry.getKey();

                for (Map.Entry<String, Integer> operationEntry
                        : secondEntry.getValue().entrySet()) {

                    writer.write(
                            timestamp
                                    + ","
                                    + escapeCsv(operationEntry.getKey())
                                    + ","
                                    + operationEntry.getValue()
                    );
                    writer.newLine();
                }
            }*/
        }
    }


    /**
     * Basic CSV escaping.
     */
    private String escapeCsv(String value) {

        if (value == null) {
            return "";
        }

        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")) {

            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

}
