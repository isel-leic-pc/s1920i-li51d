package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UnarySemaphoreKS implements UnarySemaphore {

    // Represents a request in the queue
    private static class Request {
        final Condition condition;
        boolean isDone = false;

        Request(Lock monitor) {
            condition = monitor.newCondition();
        }
    }

    private final Lock monitor = new ReentrantLock();

    // mutable state
    private int availableUnits;
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    public UnarySemaphoreKS(int initialUnits) {

        availableUnits = initialUnits;
    }

    public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
        monitor.lock();
        try {
            // happy path
            if (queue.isEmpty() && availableUnits > 0) {
                availableUnits -= 1;
                return true;
            }

            // should it wait or not
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // prepare to wait
            NodeLinkedList.Node<Request> requestNode = queue.push(new Request(monitor));
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                try {
                    requestNode.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (requestNode.value.isDone) {
                        // cannot give-up
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    // giving-up
                    queue.remove(requestNode);
                    completeRequests();
                    throw e;
                }

                // is request fulfilled (i.e. isDone)
                if (requestNode.value.isDone) {
                    return true;
                }

                remaining = Timeouts.remaining(deadline);
                // should it continue to wait
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
        while (availableUnits > 0 && queue.isNotEmpty()) {
            NodeLinkedList.Node<Request> head = queue.pull();
            head.value.isDone = true;
            availableUnits -= 1;
            head.value.condition.signal();
        }
    }
}
