package pt.isel.pc.sketches.synchronizers;

import pt.isel.pc.utils.NodeLinkedList;
import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphoreWithFifoOrder {

    private int availableUnits;
    private final NodeLinkedList<Thread> queue = new NodeLinkedList<>();

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    public SemaphoreWithFifoOrder(int initialUnits) {
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
            NodeLinkedList.Node<Thread> node = queue.push(Thread.currentThread());
            long limit = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(limit);
            while (true) {
                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // giving up
                    if (availableUnits > 0) {
                        condition.signal();
                    }
                    queue.remove(node);
                    throw e;
                }

                // is condition true
                if (queue.isHeadNode(node) && availableUnits > 0) {
                    availableUnits -= 1;
                    queue.pull();
                    return true;
                }

                remaining = Timeouts.remaining(limit);
                // should we continue to wait
                if (Timeouts.isTimeout(remaining)) {
                    // giving up
                    queue.remove(node);
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
            // TODO is this correct?
            condition.signal();
        } finally {
            monitor.unlock();
        }
    }
}
