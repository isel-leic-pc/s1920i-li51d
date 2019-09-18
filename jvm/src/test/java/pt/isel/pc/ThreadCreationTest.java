package pt.isel.pc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.Utils;

import java.util.concurrent.TimeUnit;

public class ThreadCreationTest {

    private static Logger log = LoggerFactory.getLogger(ThreadCreationTest.class);

    @Test
    public void thread_creation_example() throws InterruptedException {

        Thread th = new Thread(() -> {
            Thread.currentThread().isInterrupted();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Unexpected interruptedException", e);
                throw new RuntimeException(e);
            }
            log.info("Inside new thread");
        });
        th.start();
        th.join();
        //log.info("ending");

    }

}
