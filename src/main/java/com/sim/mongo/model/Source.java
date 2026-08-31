package com.sim.mongo.model;

import com.sim.mongo.distributions.ConstantDistribution;
import com.sim.mongo.events.OperationArrivesToNodeEvent;

import com.sim.mongo.distributions.Distribution;
import lombok.Getter;
import org.ja.Operation;
import org.ja.OperationDefinition;
import org.ja.PrimaryNode;
import org.ja.Shard;

import java.util.List;

public class Source {
    private final int id;

    @Getter
    private final Shard shard;


    private Distribution clientToNodeTravelTime = new ConstantDistribution(4.0);

    private final List<OperationDefinition> readOperationsDefinitions;
    private final List<OperationDefinition> writeOperationsDefinitions;

    public Source(int id, Shard shard, List<OperationDefinition> readOperationsDefinitions, List<OperationDefinition> writeOperationsDefinitions) {
        this.id = id;

        this.shard = shard;

        this.readOperationsDefinitions = readOperationsDefinitions;
        this.writeOperationsDefinitions = writeOperationsDefinitions;
    }


    public void scheduleThisSecondOperations(long secondStartMs){
        scheduleOperationsFromDefinitionCollection(readOperationsDefinitions, secondStartMs);
        scheduleOperationsFromDefinitionCollection(writeOperationsDefinitions, secondStartMs);

    }

    private void scheduleOperationsFromDefinitionCollection(List<OperationDefinition> definitions, long secondStartMs){
        for(OperationDefinition opDef : definitions){
            int opNumber = opDef.getRandomOpNumber();
            for(int i=0; i<opNumber; i++){
                long creationTime = secondStartMs + (long) (Math.random() * 1000.0);
                Operation op = new Operation(opDef, this, creationTime, (long) clientToNodeTravelTime.sample());
                System.out.println("scheduling operationArrive event");
                com.sim.mongo.GlobalScheduler.instance().schedule(new OperationArrivesToNodeEvent(op, creationTime+op.getClientToNodeTravelTime()));
            }
        }
    }


    public int getId() { return id; }

    public PrimaryNode getTargetShardPrimaryNode(){
        return shard.getPrimaryNode();
    }

    public void setClientToNodeTravelTime(Distribution distribution){
        clientToNodeTravelTime = distribution;
    }
}
