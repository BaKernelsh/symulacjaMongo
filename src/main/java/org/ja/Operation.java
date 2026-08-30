package org.ja;

import com.sim.mongo.GlobalScheduler;
import com.sim.mongo.events.OperationEndEvent;
import com.sim.mongo.model.Source;
import lombok.Getter;
import lombok.Setter;
import org.ja.Utils.CombinatoricsUtils;
import org.ja.Utils.OperationUtils;

import java.util.Random;

public class Operation {

    private OperationDefinition operationDefinition;
    private int affectedDocumentCount;

    private Source source;
    @Setter
    private OperationStateEnum state;

    private boolean isTransactionOperation;

    //how much time it takes to travel from client to mongos router
    @Getter
    private long clientToNodeTravelTime;
    //time of creation at client
    private long creationTime;
    private long nodeReachedTime;
    //time at which mongodb started executing operation after queuing etc.

    private long executionStartTime;

    private long baseExecutionTimeMs;
    private long executionTime;
    @Getter
    @Setter
    private OperationEndEvent endEvent;
    @Setter
    private long endTime;
    @Getter
    private long resultReachedSourceTime;

    public Operation(OperationDefinition definition, Source source, long creationTime, long clientToNodeTravelTime){
        operationDefinition = definition;

        affectedDocumentCount = definition.getRandomAffectedDocNumber();
        baseExecutionTimeMs = definition.getRandomBaseExecutionTime();

        this.creationTime = creationTime;
        this.clientToNodeTravelTime = clientToNodeTravelTime;
        this.nodeReachedTime = creationTime + clientToNodeTravelTime;
        this.source = source;
    }

    public OperationTypeEnum getType(){
        return operationDefinition.getType();
    }

    public boolean isRead(){
        return operationDefinition.getType().isRead();
    }

    public void generateConflictsWithOperation(Operation otherOperation){
        int numberOfDocsInCollection = source.getTargetShardPrimaryNode().getNumberOfDocsInCollection(operationDefinition.getAffectedCollectionName());
        //Liczba sposobów, na które można wybrać dokumenty dla tej operacji z dokumentów nieużywanych przez drugą operację
        int c1 =
           CombinatoricsUtils.numberOfCombinations(numberOfDocsInCollection - otherOperation.affectedDocumentCount, affectedDocumentCount);
        //Liczba sposobów, na które można wybrać dokumenty dla tej operacji ze wszystkich
        int c2 = CombinatoricsUtils.numberOfCombinations(numberOfDocsInCollection, affectedDocumentCount);

        double conflictProbability = 1 - ((double) c1) / ((double) c2);

        Random rand = new Random();
        long thisOpTimeIncrease = 0;
        if(rand.nextDouble() < conflictProbability){
            int numberOfConflicts = rand.nextInt(1, Math.min(affectedDocumentCount, otherOperation.affectedDocumentCount));
            int thisOpNumberOfLockWaits = numberOfConflicts / 2;
            int otherOpNumberOfLockWaits = numberOfConflicts - thisOpNumberOfLockWaits;
            for(int i=0; i<thisOpNumberOfLockWaits; i++){
                int numberOfRetries = rand.nextInt(1, 199);
                long timeWaitingOnLocks = OperationUtils.calcTotalSleepTimeForRetries(numberOfRetries);
                thisOpTimeIncrease += timeWaitingOnLocks;
            }
            updateExecutionTimeAndRescheduleEndEvent(thisOpTimeIncrease);

            long otherOpTimeIncrease = 0;
            for(int i=0; i<otherOpNumberOfLockWaits; i++){
                int numberOfRetries = rand.nextInt(1, 199);
                long timeWaitingOnLocks = OperationUtils.calcTotalSleepTimeForRetries(numberOfRetries);
                otherOpTimeIncrease += timeWaitingOnLocks;
            }
            otherOperation.updateExecutionTimeAndRescheduleEndEvent(otherOpTimeIncrease);
        }
    }

    public void arriveOperationToShard(long arriveTime){
        source.getShard().arriveOperation(this, arriveTime);
    }

    public void endOperationSuccessfully(long endTime){
        source.getTargetShardPrimaryNode().endOperationSuccessfully(this, endTime);
    }
    public void setExecutionStartTime(long time){
        executionStartTime = time;
        endEvent = new OperationEndEvent(this, executionTime + baseExecutionTimeMs);
    }


    public void setResultReachedSourceTime(){
        resultReachedSourceTime = endTime + clientToNodeTravelTime;
    }

    private void updateExecutionTimeAndRescheduleEndEvent(long executionTimeToAdd){
        executionTime += executionTimeToAdd;
        endEvent = new OperationEndEvent(this, executionStartTime + executionTime);
        GlobalScheduler.instance().rescheduleOperationEndEvent(this, endEvent);
    }
}
