package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AsyncChannelExampleTests {

    private static final Logger log = LoggerFactory.getLogger(AsyncChannelExampleTests.class);

    @Test
    public void requestExample() throws IOException, InterruptedException {
        Semaphore done = new Semaphore(0);

        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

        channel.connect(new InetSocketAddress("httpbin.org", 80), null,
          new CompletionHandler<Void, Object>() {
              @Override
              public void completed(Void result, Object attachment) {
                  log.info("connect completed");
                  String requestString = "GET https://httpbin.org/delay/2 HTTP/1.1\r\n"
                    + "User-Agent: Me\r\nHost: httpbin.org\r\n\r\n";
                  byte[] bytes = requestString.getBytes(StandardCharsets.UTF_8);
                  ByteBuffer sendBuffer = ByteBuffer.wrap(bytes);
                  channel.write(sendBuffer, null, new CompletionHandler<Integer, Object>() {
                      @Override
                      public void completed(Integer result, Object attachment) {
                          log.info("request send completed");
                          new ReadMachine(channel, Channels.newChannel(System.out), done).start();
                      }

                      @Override
                      public void failed(Throwable exc, Object attachment) {
                          log.error("send failed", exc);
                          done.release();
                      }
                  });
              }

              @Override
              public void failed(Throwable exc, Object attachment) {
                  log.error("connect failed", exc);
                  done.release();
              }
          });
        done.acquire();
    }

    class ReadMachine implements CompletionHandler<Integer, Object> {

        private final AsynchronousSocketChannel channel;
        private final WritableByteChannel dest;
        private final Semaphore sem;
        ByteBuffer receiveBuffer = ByteBuffer.allocate(32);

        public ReadMachine(AsynchronousSocketChannel channel, WritableByteChannel dest, Semaphore sem) {
            this.channel = channel;
            this.dest = dest;
            this.sem = sem;
        }

        public void start() {
            channel.read(receiveBuffer, 10, TimeUnit.SECONDS, null, this);
        }

        @Override
        public void completed(Integer result, Object attachment) {
            // log.info("read completed");
            receiveBuffer.flip();
            while (receiveBuffer.hasRemaining()) {
                try {
                    dest.write(receiveBuffer);
                } catch (IOException e) {
                    log.error("error writing to stdout", e);
                    sem.release();
                }
            }
            receiveBuffer.clear();
            channel.read(receiveBuffer, 1000, TimeUnit.MILLISECONDS, null, this);
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            if (exc instanceof InterruptedByTimeoutException) {
                log.warn("timeout on read", exc);
                sem.release();
            } else {
                log.error("error on read", exc);
                sem.release();
            }
        }
    }
}
