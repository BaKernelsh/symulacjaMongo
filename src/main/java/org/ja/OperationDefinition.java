package org.ja;

import com.sim.mongo.distributions.Distribution;
import lombok.Getter;

public class OperationDefinition {
    private String id;
    @Getter
    private String affectedCollectionName;
    @Getter
    OperationTypeEnum type;
    private Distribution operationsPerSecondDistribution;
    private Distribution affectedDocNumberDistribution;
    private Distribution baseExecutionTimeDistribution;


    public int getRandomOpNumber(){
        return (int) operationsPerSecondDistribution.sample();
    }

    public int getRandomAffectedDocNumber(){
        return (int) affectedDocNumberDistribution.sample();
    }

    public int getRandomBaseExecutionTime(){
        return (int) baseExecutionTimeDistribution.sample();
    }

}
