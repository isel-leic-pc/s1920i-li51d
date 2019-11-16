package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;

public class AsyncHttpRequestExampleTests {

    private static final Logger log = LoggerFactory.getLogger(AsyncHttpRequestExampleTests.class);

    @Test
    public void test() throws IOException, InterruptedException {

        // NIO - New IO - non-blocking IO based on selectors.
        // NIO2 - New IO 2 - non-blocking IO based on (very-limited) futures and callbacks
        // Concepts:
        // - Buffers (e.g. ByteBuffer).
        // - Channel - communication interfaces
        // - CompletionHandler - callback called when an operation is completed
        Semaphore done = new Semaphore(0);

        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        log.info("starting connect");
        channel.connect(new InetSocketAddress("httpbin.com", 80), null,
                new CompletionHandler<Void, Object>() {
                    @Override
                    public void completed(Void result, Object attachment) {
                        log.info("connect completed with success");
                        done.release();
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        log.info("connect completed with error - {}", exc.getClass().getName());
                        done.release();
                    }
                });
        log.info("connect returned");

        done.acquire();
        log.info("ending");
    }

}
