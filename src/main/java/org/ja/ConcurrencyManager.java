package org.ja;

import com.sim.mongo.GlobalScheduler;
import com.sim.mongo.events.ResultArrivesToSourceEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConcurrencyManager {

    private Queue<Operation> waitingForTicketQ = new LinkedBlockingQueue<>();
    private List<Operation> readsExecuting = new LinkedList<>();
    private List<Operation> writesExecuting = new LinkedList<>();
    private int concurrentReadTransactionsAllowed = 128;
    private int concurrentWriteTransactionsAllowed = 128;


    public ConcurrencyManager(int concurrentReadTransactionsAllowed, int concurrentWriteTransactionsAllowed){
        this.concurrentReadTransactionsAllowed = concurrentReadTransactionsAllowed;
        this.concurrentWriteTransactionsAllowed = concurrentWriteTransactionsAllowed;
    }

    public ConcurrencyManager(){}

    public void addOperation(Operation operation, long arriveTime){
        if(operation.isRead()) {
            giveReadTicketOrQueue(operation, arriveTime);
        }

        else {
            giveWriteTicketOrQueue(operation, arriveTime);
        }

    }

    private void generateConflictsWithWrites(Operation operation){
        writesExecuting.forEach(writeOp ->{
            if (writeOp != operation) {
                operation.generateConflictsWithOperation(writeOp);
            }
        });
    }

    private void giveReadTicketOrQueue(Operation op, long arriveTime){
        if (readsExecuting.size() < concurrentReadTransactionsAllowed) {
            readsExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            op.setExecutionStartTimeAndScheduleEndEvent(arriveTime);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    private void giveWriteTicketOrQueue(Operation op, long arriveTime){
        if (writesExecuting.size() < concurrentWriteTransactionsAllowed) {
            writesExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            op.setExecutionStartTimeAndScheduleEndEvent(arriveTime);
            generateConflictsWithWrites(op);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    public void endOperationSuccessfully(Operation operation, long endTime){
        if(operation.isRead()) {
            readsExecuting.remove(operation);
            startQueuedOperation(true, endTime);
        }
        else{
            //System.out.println("usunieto operacje z wykonywanych" + writesExecuting.remove(operation));
            writesExecuting.remove(operation);
            startQueuedOperation(false, endTime);
        }
        operation.setEndTime(endTime);
        operation.setResultReachedSourceTime();
    }

    private void giveReadTicketAfterQueuing(Operation op, long previousOpEndTime){
        readsExecuting.add(op);
        op.setState(OperationStateEnum.EXECUTING);
        op.setExecutionStartTimeAndScheduleEndEvent(previousOpEndTime);
        generateConflictsWithWrites(op);
    }

    private void giveWriteTicketAfterQueuing(Operation op, long previousOpEndTime){
        writesExecuting.add(op);
        op.setState(OperationStateEnum.EXECUTING);
        op.setExecutionStartTimeAndScheduleEndEvent(previousOpEndTime);
        generateConflictsWithWrites(op);
    }

    private void startQueuedOperation(boolean read, long previousOpEndTime){
        if(read){
            Operation firstWaitingOp = getFirstWaitingOp(true);
            if(firstWaitingOp!=null){
                giveReadTicketAfterQueuing(firstWaitingOp, previousOpEndTime);
            }
        }
        else{
            Operation firstWaitingOp = getFirstWaitingOp(false);
            if(firstWaitingOp!=null){
                giveWriteTicketAfterQueuing(firstWaitingOp, previousOpEndTime);
            }
        }
    }

    private Operation getFirstWaitingOp(boolean read){
        for (Operation op : waitingForTicketQ) {
            if ((op.isRead() && read) || (!op.isRead() && !read) )  {
                if (waitingForTicketQ.remove(op)) {
                    return op;
                }
            }
        }
        return null;
    }



}
