package org.ja;

import lombok.Setter;
import org.ja.Utils.CombinatoricsUtils;
import org.ja.Utils.OperationUtils;

import java.util.Random;

public class Operation {

    private OperationDefinition operationDefinition;
    private int affectedDocumentCount;

    private PrimaryNode targetNode;
    @Setter
    private OperationStateEnum state;

    private boolean isTransactionOperation;

    //how much time it takes to travel from client to mongos router
    private long clientToMongosTravelTime;
    //how much time it takes to travel from mongos to node
    private long mongosToNodeTravelTime;
    //time of creation at client
    private long creationTime;
    private long nodeReachedTime;
    //time at which mongodb started executing operation after queuing etc.
    private long executionStartTime;

    private long baseExecutionTimeMs;
    private long executionTime;

    public OperationTypeEnum getType(){
        return operationDefinition.getType();
    }

    public boolean isRead(){
        return operationDefinition.getType().isRead();
    }

    public void generateConflictsWithOperation(Operation otherOperation){
        int numberOfDocsInCollection = targetNode.getNumberOfDocsInCollection(operationDefinition.getAffectedCollectionName());
        //Liczba sposobów, na które można wybrać dokumenty dla tej operacji z dokumentów nieużywanych przez drugą operację
        int c1 =
           CombinatoricsUtils.numberOfCombinations(numberOfDocsInCollection - otherOperation.affectedDocumentCount, affectedDocumentCount);
        //Liczba sposobów, na które można wybrać dokumenty dla tej operacji ze wszystkich
        int c2 = CombinatoricsUtils.numberOfCombinations(numberOfDocsInCollection, affectedDocumentCount);

        double conflictProbability = 1 - ((double) c1) / ((double) c2);

        Random rand = new Random();

        if(rand.nextDouble() < conflictProbability){
            int numberOfConflicts = rand.nextInt(1, Math.min(affectedDocumentCount, otherOperation.affectedDocumentCount));
            int thisOpNumberOfLockWaits = numberOfConflicts / 2;
            int otherOpNumberOfLockWaits = numberOfConflicts - thisOpNumberOfLockWaits;
            for(int i=0; i<thisOpNumberOfLockWaits; i++){
                int numberOfRetries = rand.nextInt(1, 199);
                long timeWaitingOnLocks = OperationUtils.calcTotalSleepTimeForRetries(numberOfRetries);
                executionTime += timeWaitingOnLocks;
            }
            for(int i=0; i<otherOpNumberOfLockWaits; i++){
                int numberOfRetries = rand.nextInt(1, 199);
                long timeWaitingOnLocks = OperationUtils.calcTotalSleepTimeForRetries(numberOfRetries);
                otherOperation.executionTime += timeWaitingOnLocks;
            }
            //TODO update eventu zakończenia operacji
        }
    }



}
