package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleUnarySemaphoreWithLocks implements UnarySemaphore {

    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    // mutable state
    private long units;

    public SimpleUnarySemaphoreWithLocks(long initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("initial units must not be negative");
        }
        units = initial;
    }

    public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
        try {
            mon.lock();

            // happy-path
            if (units > 0) {
                units -= 1;
                return true;
            }

            // should it wait?
            if (Timeouts.noWait(timeout)) {
                return false;
            }

            // prepare to wait
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                try {
                    cond.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (units > 0) {
                        // Ensure signal is not lost if the selected thread from the wait set
                        // is interrupted while waiting for the mutual-exclusion
                        mon.notify();
                    }
                    throw e;
                }
                // Evaluate condition
                if (units > 0) {
                    units -= 1;
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    // Give-up. No additional processing is required because units == 0
                    return false;
                }
            }
        } finally {
            mon.unlock();
        }
    }

    public void release() {
        try {
            mon.lock();
            units += 1;
            cond.signal();
        } finally {
            mon.unlock();
        }
    }
}
