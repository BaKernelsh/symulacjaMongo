package org.ja;

import lombok.Getter;

public class OperationDefinition {
    private String id;
    @Getter
    private String affectedCollectionName;
    @Getter
    OperationTypeEnum type;
    private IDistribution operationsPerSecondDistribution;
    private IDistribution affectedDocNumberDistribution;



}
