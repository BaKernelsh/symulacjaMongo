package org.ja.statistics;

import org.ja.Operation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Locking {

    private boolean lockTimesByIDSorted = false;
    private boolean lockTimesSorted = false;

    private Map<String, List<Long>> operationIDToLockWaitTimes = new ConcurrentHashMap<>();
    private List<Long> allLockWaitTimes = Collections.synchronizedList(new ArrayList<>());

    public long getTotalWaitedCount(){
        return allLockWaitTimes.size();
    }


    public void recordLockWaitTime(Operation operation) {

        long lockWaitTime = operation.getExecutionPlusLocksTime() - operation.getBaseExecutionTimeMs();

        if(lockWaitTime!= 0) {
            String opID = operation.getID();
            if (operationIDToLockWaitTimes.containsKey(opID))
                operationIDToLockWaitTimes.get(opID).add(lockWaitTime);
            else {
                List<Long> newList = new ArrayList<>();
                newList.add(lockWaitTime);
                operationIDToLockWaitTimes.put(opID, newList);
            }

            allLockWaitTimes.add(lockWaitTime);
        }
    }

    private void sortLockTimesByID(){
        if(!lockTimesByIDSorted)
            operationIDToLockWaitTimes.forEach((opID, lockTimes) -> {
                Collections.sort(lockTimes);
            });
    }

    private void sortLockTimesAll(){
        if(!lockTimesSorted)
            Collections.sort(allLockWaitTimes);
    }

    /**
     * Get total lock wait time for all operations
     */
    public long getTotalLockWaitTimeAllOps() {
        return allLockWaitTimes.stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Get  wait time stats for all operations
     */
    public Stats getLockWaitTimeStatsAllOps() {
        sortLockTimesAll();
        return new Stats(allLockWaitTimes);
    }

    /**
     * Get lock wait time stats by operation id
     */
    public Map<String, Stats> getLockWaitTimeStatsByOperation() {
        Map<String, Stats> opIDToStats = new HashMap<>();

        sortLockTimesByID();

        operationIDToLockWaitTimes.forEach((opID, lockTimes) -> {
            opIDToStats.put(opID, new Stats(lockTimes));
        });

        return opIDToStats;
    }

    /**
     * Get percentage of operations that waited for locks (all operations)
     */
    public double getPercentageOfOpsWaitedForLocksAllOps(long totalOperationsCompleted) {
        if (totalOperationsCompleted == 0) return 0.0;
        return ((double) allLockWaitTimes.size() / totalOperationsCompleted) * 100.0;
    }

    /**
     * Get percentage of operations that waited for locks (by operation type)
     */
    public Map<String, Double> getPercentageOfOpsWaitedForLocksByOperation(Map<String, Integer> opIDToCount) {
        Map<String, Double> opIDToPercentage = new HashMap<>();

        operationIDToLockWaitTimes.forEach((opID, lockTimes) -> {

            if(opIDToCount.get(opID) == 0)
                opIDToPercentage.put(opID, 0.0);
            else
                opIDToPercentage.put(opID, ((double)lockTimes.size() / (double) opIDToCount.get(opID)) * 100.0);
        });

        return opIDToPercentage;
    }

    public long getOpsWaitedForLockCountForOpID(String opID){
        return operationIDToLockWaitTimes.get(opID).size();
    }

}
