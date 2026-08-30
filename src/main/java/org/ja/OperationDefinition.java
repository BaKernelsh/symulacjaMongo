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


    public void setId(String id) { this.id = id; }
    public void setAffectedCollectionName(String name) { this.affectedCollectionName = name; }
    public void setType(OperationTypeEnum type) { this.type = type; }
    public void setOperationsPerSecondDistribution(com.sim.mongo.distributions.Distribution d) { this.operationsPerSecondDistribution = d; }
    public void setAffectedDocNumberDistribution(com.sim.mongo.distributions.Distribution d) { this.affectedDocNumberDistribution = d; }
    public void setBaseExecutionTimeDistribution(com.sim.mongo.distributions.Distribution d) { this.baseExecutionTimeDistribution = d; }

    public void setTypeFromString(String type){
        switch(type) {
            case "FIND":
                this.type = OperationTypeEnum.FIND;
                break;
            case "INSERT":
                this.type = OperationTypeEnum.INSERT;
                break;
            case "UPDATE":
                this.type = OperationTypeEnum.UPDATE;
                break;
            case "DELETE":
                this.type = OperationTypeEnum.DELETE;
                break;
            case "AGGREGATE":
                this.type = OperationTypeEnum.AGGREGATE;
                break;
        }
    }
}
