package org.ja;

import java.util.HashMap;

public class PrimaryNode {

    private HashMap<String, Collection> collectionsByName;

    private ConcurrencyManager concurrencyManager;

    public PrimaryNode(ConcurrencyManager concurrencyManager){
        this.concurrencyManager = concurrencyManager;
    }

    public void arriveOperation(Operation op, long arriveTime){
        concurrencyManager.addOperation(op, arriveTime);
    }

    public void endOperationSuccessfully(Operation operation, long endTime){
        concurrencyManager.endOperationSuccessfully(operation, endTime);
    }

    public Collection getCollectionByName(String name){
        return collectionsByName.get(name);
    }

    public int getNumberOfDocsInCollection(String collectionName){
        return getCollectionByName(collectionName).getNumberOfDocuments();
    }

}
