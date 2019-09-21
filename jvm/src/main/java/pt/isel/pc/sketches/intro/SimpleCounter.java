package pt.isel.pc.sketches.intro;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCounter {
    private final Lock lock = new ReentrantLock();
    private final Object lock2 = new Object();

    // value is protected by lock
    // any access (read or write) must be done with lock acquired
    private long value = 0;

    public long increment() {
        lock.lock();
        try {
            for (long l = 0; l < 5_000_000_000L; ++l) {
                value += 1;
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public long decrement() {
        lock.lock();
        try {
            for (long l = 0; l < 5_000_000_000L; ++l) {
                value -= 1;
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public long increment2() {
        synchronized (lock2) {
            for (long l = 0; l < 5_000_000_000L; ++l) {
                value += 1;
            }
            return value;
        }
    }

    public long increment3() {
        synchronized (this) {
            for (long l = 0; l < 5_000_000_000L; ++l) {
                value += 1;
            }
            return value;
        }
    }

    public synchronized long increment4() {
        for (long l = 0; l < 5_000_000_000L; ++l) {
            value += 1;
        }
        return value;
    }
}
