package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NarySemaphoreKS {

    private static class Request {
        final int requestedUnits;
        final Condition condition;
        boolean isDone = false;

        Request(int requestedUnits, Condition condition) {
            this.requestedUnits = requestedUnits;
            this.condition = condition;
        }
    }

    // mutable state
    private int units;
    private final NodeLinkedList<Request> q = new NodeLinkedList<>();

    private final Lock monitor = new ReentrantLock();

    public NarySemaphoreKS(int initial) {
        units = initial;
    }

    public boolean acquire(int requestedUnits, long timeout, TimeUnit timeUnit) throws InterruptedException {

        try {
            monitor.lock();

            // fast path
            if (q.isEmpty() && units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }

            // should it wait or not?
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // prepare everything for waiting
            NodeLinkedList.Node<Request> node = q.push(
              new Request(requestedUnits, monitor.newCondition()));
            long deadline = Timeouts.start(timeout, timeUnit);
            long remainingInMs = Timeouts.remaining(deadline);

            while (true) {
                try {
                    node.value.condition.await(remainingInMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (node.value.isDone) {
                        // unable to give up
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    // giving up
                    q.remove(node);
                    completeRequests();
                    throw e;
                }

                // is request fulfilled (i.e. isDone)
                if (node.value.isDone) {
                    // if isDone is true, then all the leave processing is already done
                    return true;
                }

                // should it wait
                remainingInMs = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remainingInMs)) {
                    // giving up
                    q.remove(node);
                    completeRequests();
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    public void release(int releasedUnits) {
        try {
            monitor.lock();
            units += releasedUnits;
            completeRequests();
        } finally {
            monitor.unlock();
        }
    }

    private void completeRequests() {
        while (!q.isEmpty() && units >= q.getHeadValue().requestedUnits) {
            // The signaling thread does the processing
            // - acquire the units
            // - and remove from queue
            // on behalf of the signaled thread
            NodeLinkedList.Node<Request> node = q.pull();
            node.value.isDone = true;
            units -= node.value.requestedUnits;
            node.value.condition.signal();
        }
    }
}
