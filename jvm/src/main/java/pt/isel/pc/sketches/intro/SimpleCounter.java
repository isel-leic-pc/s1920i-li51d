package pt.isel.pc.sketches.intro;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleCounter {
    private final Lock lock = new ReentrantLock();

    // value is protected by lock
    private long value = 0;

    public long increment() {
        lock.lock();
        for (long l = 0; l < 5_000_000_000L; ++l) {
            value += 1;
        }
        lock.unlock();
        return value;
    }
}
