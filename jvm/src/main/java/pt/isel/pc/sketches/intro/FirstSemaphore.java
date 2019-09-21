package pt.isel.pc.sketches.intro;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FirstSemaphore {

    private int availableUnits;
    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();

    public FirstSemaphore(int initialUnits) {
        this.availableUnits = initialUnits;
    }

    public void acquire() throws InterruptedException {
        monitor.lock();
        try {
            // "while" is required due to barging (and spurious wake-ups)
            while (!(availableUnits > 0)) {
                // wait for available units
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    /* - option 1 - give-up and propagate signal
                     *if (availableUnits > 0) {
                     *
                     *   condition.signal();
                     *}
                     *throw e;
                     */
                    // - option 2
                    if(availableUnits > 0) {
                        availableUnits -= 1;
                        Thread.currentThread().interrupt();
                        return;
                    }
                    throw e;
                }
            }
            // here we have the guarantee that availableUnits > 0
            availableUnits -= 1;

        } finally {
            monitor.unlock();
        }
    }

    public void release() {
        monitor.lock();
        try {
            availableUnits += 1;
            condition.signal();
        } finally {
            monitor.unlock();
        }
    }
}
