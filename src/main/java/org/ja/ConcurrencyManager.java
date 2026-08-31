package org.ja;

import com.sim.mongo.GlobalScheduler;
import com.sim.mongo.events.ResultArrivesToSourceEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConcurrencyManager {

    private Queue<Operation> waitingForTicketQ = new LinkedBlockingQueue<>();
    private List<Operation> readsExecuting = new LinkedList<>();
    private List<Operation> writesExecuting = new LinkedList<>();
    private int concurrentReadTransactions = 128;
    private int concurrentWriteTransactions = 128;


    public ConcurrencyManager(int concurrentReadTransactions, int concurrentWriteTransactions){
        this.concurrentReadTransactions = concurrentReadTransactions;
        this.concurrentWriteTransactions = concurrentWriteTransactions;
    }

    public ConcurrencyManager(){}

    public void addOperation(Operation operation, long arriveTime){
        if(operation.isRead()) {
            giveReadTicketOrQueue(operation, arriveTime);
            //TODO wstawienie eventu zakonczenia operacji
        }

        else {
            giveWriteTicketOrQueue(operation, arriveTime);
        }

    }

    private void generateConflictsWithWrites(Operation operation){
        writesExecuting.forEach(writeOp ->{
            operation.generateConflictsWithOperation(writeOp);
        });
    }

    private void giveReadTicketOrQueue(Operation op, long arriveTime){
        if (readsExecuting.size() < 128) {
            readsExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            op.setExecutionStartTime(arriveTime);
            generateConflictsWithWrites(op);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    private void giveWriteTicketOrQueue(Operation op, long arriveTime){
        if (writesExecuting.size() < 128) {
            writesExecuting.add(op);
            op.setState(OperationStateEnum.EXECUTING);
            op.setExecutionStartTime(arriveTime);
            GlobalScheduler.instance().schedule(op.getEndEvent());
            generateConflictsWithWrites(op);
        }
        else{
            waitingForTicketQ.add(op);
            op.setState(OperationStateEnum.IN_QUEUE);
        }
    }

    public void endOperationSuccessfully(Operation operation, long endTime){
        if(operation.isRead()) {
            readsExecuting.remove(operation); //TODO equals
            startNewOperation(true, endTime);
        }
        else{
            writesExecuting.remove(operation);
            startNewOperation(false, endTime);
        }
        operation.setEndTime(endTime);
        operation.setResultReachedSourceTime();
        GlobalScheduler.instance().schedule(new ResultArrivesToSourceEvent(operation.getResultReachedSourceTime()));
    }

    private void giveReadTicketAfterQueuing(Operation op, long previousOpEndTime){
        readsExecuting.add(op);
        op.setState(OperationStateEnum.EXECUTING);
        op.setExecutionStartTime(previousOpEndTime);
        generateConflictsWithWrites(op);
    }

    private void giveWriteTicketAfterQueuing(Operation op, long previousOpEndTime){
        writesExecuting.add(op);
        op.setState(OperationStateEnum.EXECUTING);
        op.setExecutionStartTime(previousOpEndTime);
        generateConflictsWithWrites(op);
    }

    private void startNewOperation(boolean read, long previousOpEndTime){
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
