package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Cluster;
import com.sim.mongo.model.Operation;
import com.sim.mongo.model.Shard;
import com.sim.mongo.GlobalScheduler;

public class OperationCompleteEvent extends Event {
    private final Operation op;
    private final Cluster cluster;

    public OperationCompleteEvent(long time, Operation op, Cluster cluster) {
        super(time);
        this.op = op;
        this.cluster = cluster;
    }

    @Override
    public void process() {
        // release locks
        for (int sid : op.getShards()) {
            Shard s = cluster.getShard(sid);
            if (op.getType() == Operation.Type.READ) s.releaseShared();
            else s.releaseExclusive();
        }
        // notify client
        long waiting = op.getLockAcquiredTime() - op.getSubmitTime();
        op.getClient().recordResponse(op.getSubmitTime(), time, waiting);
    }
}
