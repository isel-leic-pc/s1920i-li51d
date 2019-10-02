package pt.isel.pc.examples.synchronizers;

import java.util.concurrent.TimeUnit;

public interface UnarySemaphore {
    boolean acquire(long timeout, TimeUnit timeoutUnit) throws InterruptedException;
    void release();
}
