package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.TestHelper;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class OrderingUnarySemaphoreTests {

    private static final int N_OF_THREADS = 100;
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);
    private static final Logger logger = LoggerFactory.getLogger(OrderingUnarySemaphoreTests.class);

    private final TestHelper helper = new TestHelper(TEST_DURATION);

    private void test(UnarySemaphore sem) throws InterruptedException {
        final ConcurrentLinkedQueue<Long> units = new ConcurrentLinkedQueue<>();
        helper.createAndStartMultiple(N_OF_THREADS, isDone -> {
            long acquiredUnits = 0;
            while (!isDone.get() && acquiredUnits < Long.MAX_VALUE) {
                sem.acquire(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                acquiredUnits += 1;
                sem.release();
            }
            units.add(acquiredUnits);
        });
        helper.join();
        long max = Collections.max(units);
        long min = Collections.min(units);
        logger.info("min acquired units = {}, max acquired units = {}, diff = {}",
          max, min, max - min);
        if(max - min > 0.01 * min) {
            throw new AssertionError("acquired units (max-min) exceeds 1% of min");
        }
    }

    @Test
    public void test_queue_based_semaphore() throws InterruptedException {
        test(new UnarySemaphoreWithFifoOrderAndSpecificNotification(1));
    }

    @Test
    public void test_ks_based_semaphore() throws InterruptedException {
        test(new UnarySemaphoreKS(1));
    }
}
