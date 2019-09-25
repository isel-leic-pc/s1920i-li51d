package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphoreKS {

    private static class Request {
        final Condition condition;
        boolean isDone = false;
        Request(Lock monitor){
            condition = monitor.newCondition();
        }
    }

    private int availableUnits;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    private final Lock monitor = new ReentrantLock();

    public SemaphoreKS(int initialUnits) {
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
                    if(requestNode.value.isDone) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    // give-up
                    queue.remove(requestNode);
                    completeRequests();
                    throw e;
                }

                // is request fulfilled (i.e. isDone)
                if (requestNode.value.isDone) {
                    return true;
                }

                remaining = Timeouts.remaining(limit);
                // should we continue to wait
                if (Timeouts.isTimeout(remaining)) {
                    // giving up
                    queue.remove(requestNode);
                    completeRequests();
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
            completeRequests();
        } finally {
            monitor.unlock();
        }
    }

    private void completeRequests() {
        while(availableUnits > 0 && queue.isNotEmpty()) {
            NodeLinkedList.Node<Request> head = queue.pull();
            head.value.isDone = true;
            availableUnits -= 1;
            head.value.condition.signal();
        }
    }
}
