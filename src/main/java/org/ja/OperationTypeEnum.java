package org.ja;

public enum OperationTypeEnum {
    FIND,
    INSERT,
    UPDATE,
    DELETE,
    AGGREGATE;

    public boolean isRead(){
        return this.equals(OperationTypeEnum.FIND);
    }

}
