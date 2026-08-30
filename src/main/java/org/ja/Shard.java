package org.ja;

import lombok.Getter;


public class Shard {
    @Getter
    private PrimaryNode primaryNode;
    private String primaryNodeId;

    public void arriveOperation(Operation operation, long arriveTime){
        primaryNode.arriveOperation(operation,arriveTime);
    }


}
