package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManualResetEventWithBatching {

    private static class BatchRequest {
        boolean isDone = false;
    }

    // ManualResetEvent can be in the "set" (true) or "reset" (false) state
    private boolean state;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private BatchRequest currentBatchRequest = new BatchRequest();

    public ManualResetEventWithBatching(boolean initialState) {
        state = initialState;
    }

    public void reset() {
        monitor.lock();
        try{
            if(state == false) {
                return;
            }
            state = false;
            currentBatchRequest = new BatchRequest();
        }finally {
            monitor.unlock();
        }
    }

    public void set() {
        monitor.lock();
        try{
            if(state == true) {
                return;
            }
            state = true;
            currentBatchRequest.isDone = true;
            condition.signalAll();
        }finally {
            monitor.unlock();
        }
    }

    // waits for the state to be set (i.e true)
    public boolean await(long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        monitor.lock();
        try{
            // happy path
            if(state == true) {
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long limit = Timeouts.start(timeout, timeoutUnit);
            long remaining = Timeouts.remaining(limit);
            BatchRequest myBatch = currentBatchRequest;
            while (true) {
                // TODO the absence of the catch needs some explanation
                condition.await(remaining, TimeUnit.MILLISECONDS);

                // is condition true
                if (myBatch.isDone) {
                    return true;
                }

                remaining = Timeouts.remaining(limit);
                // should we continue to wait
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        }finally {
            monitor.unlock();
        }
    }

}
