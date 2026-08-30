package com.sim.mongo.events;

import com.sim.mongo.Event;
import com.sim.mongo.model.Cluster;
import com.sim.mongo.model.Operation;
import com.sim.mongo.model.Shard;
import com.sim.mongo.GlobalScheduler;
import com.sim.mongo.events.OperationCompleteEvent;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Try to acquire locks for an operation. If any lock fails, release acquired locks and retry after backoff.
 */
public class TryLockEvent extends Event {
    private final Operation op;
    private final Cluster cluster;
    private static final long RETRY_BACKOFF_MS = 5; // simplification

    public TryLockEvent(long time, Operation op, Cluster cluster) {
        super(time);
        this.op = op;
        this.cluster = cluster;
    }

    @Override
    public void process() {
        // To avoid deadlocks we acquire shard locks in ascending order of shard id
        List<Integer> shards = op.getShards();
        Collections.sort(shards);
        boolean allAcquired = true;
        // attempt acquire
        for (int sid : shards) {
            Shard s = cluster.getShard(sid);
            boolean ok;
            if (op.getType() == Operation.Type.READ) ok = s.tryAcquireShared();
            else ok = s.tryAcquireExclusive();
            if (!ok) {
                allAcquired = false;
                break;
            }
        }
        if (!allAcquired) {
            // release any that were acquired
            for (int sid : shards) {
                Shard s = cluster.getShard(sid);
                // best-effort release: if we hold it
                // check op type
                if (op.getType() == Operation.Type.READ) {
                    try { s.releaseShared(); } catch (Exception ignored) {}
                } else {
                    try { s.releaseExclusive(); } catch (Exception ignored) {}
                }
            }
            // schedule retry
            GlobalScheduler.instance().schedule(new TryLockEvent(time + RETRY_BACKOFF_MS, op, cluster));
            return;
        }

        // record lock acquired time
        op.setLockAcquiredTime(time);
        // schedule completion
        long completion = time + (long)Math.max(1, Math.round(op.getDurationMs()));
        GlobalScheduler.instance().schedule(new OperationCompleteEvent(completion, op, cluster));
    }
}
