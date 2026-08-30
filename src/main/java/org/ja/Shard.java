package org.ja;

import lombok.Getter;


public class Shard {
    @Getter
    private PrimaryNode primaryNode = new PrimaryNode(new ConcurrencyManager());
    private String primaryNodeId;

    public void arriveOperation(Operation operation, long arriveTime){
        primaryNode.arriveOperation(operation,arriveTime);
    }


    public void addCollection(String name, int numberOfDocs){
        primaryNode.addCollection(name, numberOfDocs);
    }

}
