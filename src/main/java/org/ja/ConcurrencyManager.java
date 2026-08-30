package org.ja;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConcurrencyManager {

    private Queue<Operation> waitingForTicketQ = new LinkedBlockingQueue<>();
    private List<Operation> readsExecuting;
    private List<Operation> writesExecuting;
    private int concurrentReadTransactions = 128;
    private int concurrentWriteTransactions = 128;


    private List<Transaction> transactions;

    public ConcurrencyManager(int concurrentReadTransactions, int concurrentWriteTransactions){
        this.concurrentReadTransactions = concurrentReadTransactions;
        this.concurrentWriteTransactions = concurrentWriteTransactions;
    }

    public void addOperation(Operation operation){
        if(operation.isRead()) {
            giveReadTicketOrQueue(operation);
            //TODO wstawienie eventu zakonczenia operacji
        }

        else {
            giveWriteTicketOrQueue(operation);
        }

    }

    private void generateConflictsWithWrites(Operation operation){
        writesExecuting.forEach(writeOp ->{
            operation.generateConflictsWithOperation(writeOp);
        });
    }

    private void giveReadTicketOrQueue(Operation op){
        if (readsExecuting.size() < 128) {
            readsExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            generateConflictsWithWrites(op);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    private void giveWriteTicketOrQueue(Operation op){
        if (writesExecuting.size() < 128) {
            writesExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            generateConflictsWithWrites(op);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    public void addTransactionOperation(){

    }

}
