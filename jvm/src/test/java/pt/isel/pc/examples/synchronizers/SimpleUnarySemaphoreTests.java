package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.Timeouts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;

public class SimpleUnarySemaphoreTests {

    private static final Logger logger = LoggerFactory.getLogger(SimpleUnarySemaphoreTests.class);
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);

    private int acquireAndCheckElapsed(UnarySemaphore sem, long timeout)
      throws InterruptedException {
        boolean success;
        int tries = 0;
        // allows for a 100ms error due to scheduling delays
        long allowedTimeError = 100;

        do {
            long start = System.currentTimeMillis();
            success = sem.acquire(timeout, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - start;
            tries += 1;

            if (Math.abs(duration - timeout) > allowedTimeError) {
                logger.error("acquire was {} but should not exceed {}", duration, timeout);
                if (success) {
                    sem.release();
                }
                throw new RuntimeException("Acquire exceeded allowed time");
            }

        } while (!success);

        return tries;
    }

    public void test(UnarySemaphore sem, int nOfThreads, int initialUnits) throws InterruptedException {

        final List<Thread> ths = new ArrayList<>();
        final AtomicInteger counter = new AtomicInteger(initialUnits);
        final AtomicBoolean error = new AtomicBoolean();
        final Instant deadline = Instant.now().plus(TEST_DURATION);
        final long acquireTimeout = 10;

        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                try {
                    while (true) {
                        if (Instant.now().compareTo(deadline) > 0) {
                            return;
                        }
                        int tries = acquireAndCheckElapsed(sem, acquireTimeout);
                        try {
                            int newValue = counter.decrementAndGet();
                            if (newValue < 0) {
                                logger.error("Too many units were acquired");
                                error.set(true);
                                return;
                            }
                            // logger.info("Acquired unit after {} tries", tries);
                            Thread.sleep(100);
                        } finally {
                            counter.incrementAndGet();
                            sem.release();
                        }
                    }
                } catch (InterruptedException e) {
                    logger.info("interruped, giving up");
                } catch (RuntimeException e) {
                    error.set(true);
                }
            });
            th.start();
            ths.add(th);
        }


        // let's interrupt some threads to see what happens
        Duration interruptPeriod = TEST_DURATION.dividedBy(nOfThreads/3);
        for (int i = 0; i < nOfThreads; i += 3) {
            ths.get(i).interrupt();
            Thread.sleep(interruptPeriod.toMillis());
        }

        // join then all
        long testDeadline = Timeouts.start(
          TEST_DURATION.plusSeconds(5).getSeconds(),
          TimeUnit.SECONDS);
        for (Thread th : ths) {
            long remaining = Timeouts.remaining(testDeadline);
            th.join(remaining);
            if (th.isAlive()) {
                logger.error("Test didn't stop when it was supposed to");
            }
        }
        assertFalse(error.get());
    }

    @Test
    public void test_simple_semaphore() throws InterruptedException {
        int nOfThreads = 100;
        int initialUnits = nOfThreads / 2;
        test(new SimpleUnarySemaphoreWithLocks(initialUnits), nOfThreads, initialUnits);
    }

    @Test
    public void test_queue_based_semaphore() throws InterruptedException {
        int nOfThreads = 100;
        int initialUnits = nOfThreads / 2;
        test(new UnarySemaphoreWithFifoOrderAndSpecificNotification(initialUnits),
          nOfThreads, initialUnits);
    }

    @Test
    public void test_ks_based_semaphore() throws InterruptedException {
        int nOfThreads = 100;
        int initialUnits = nOfThreads / 2;
        test(new UnarySemaphoreKS(initialUnits),
          nOfThreads, initialUnits);
    }

}
