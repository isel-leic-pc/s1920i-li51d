package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphoreWithFifoOrderAndSpecificNotification {

    private static class Request {
        final Condition condition;
        Request(Lock monitor){
            condition = monitor.newCondition();
        }
    }

    private int availableUnits;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    private final Lock monitor = new ReentrantLock();

    public SemaphoreWithFifoOrderAndSpecificNotification(int initialUnits) {
        this.availableUnits = initialUnits;
    }

    public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
        monitor.lock();
        try {
            // happy path
            if (queue.isEmpty() && availableUnits > 0) {
                availableUnits -= 1;
                return true;
            }
            // should we wait or not
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            // prepare to wait
            NodeLinkedList.Node<Request> requestNode = queue.push(new Request(monitor));
            long limit = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(limit);
            while (true) {
                try {
                    requestNode.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    queue.remove(requestNode);
                    signalIfNeeded();
                    throw e;
                }

                // is condition true
                if (queue.isHeadNode(requestNode) && availableUnits > 0) {
                    availableUnits -= 1;
                    queue.pull();
                    signalIfNeeded();
                    return true;
                }

                remaining = Timeouts.remaining(limit);
                // should we continue to wait
                if (Timeouts.isTimeout(remaining)) {
                    // giving up
                    queue.remove(requestNode);
                    signalIfNeeded();
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    public void release() {
        monitor.lock();
        try {
            availableUnits += 1;
            signalIfNeeded();
        } finally {
            monitor.unlock();
        }
    }

    private void signalIfNeeded() {
        if(availableUnits > 0 && queue.isNotEmpty()) {
            queue.getHeadValue().condition.signal();
        }
    }
}
