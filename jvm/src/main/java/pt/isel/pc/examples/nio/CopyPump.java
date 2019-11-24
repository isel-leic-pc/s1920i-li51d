package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.CompositeThrowable;
import pt.isel.pc.utils.TriConsumer;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class CopyPump {

    private static final Logger logger = LoggerFactory.getLogger(CopyPump.class);

    private final ByteBuffer[] buffers = new ByteBuffer[] {
      ByteBuffer.allocate(1),
      ByteBuffer.allocate(1),
    };

    private final BiConsumer<ByteBuffer, CompletionHandler<Integer, Object>> read;
    private final TriConsumer<ByteBuffer, Integer, CompletionHandler<Integer, Object>> write;
    private final CompletionHandler<Integer, Object> handler;
    private final AtomicInteger pending = new AtomicInteger();

    private int readBufferIx = 0;
    private int writePosition = 0;
    private boolean readCompleted = false;
    private boolean lastWrite = false;
    private Throwable readException = null;
    private Throwable writeException = null;

    private int getReadBufferIx() {
        return readBufferIx;
    }

    private int getWriteBufferIx() {
        return (readBufferIx + 1) % 2;
    }

    private void flipBuffers() {
        readBufferIx = (readBufferIx + 1) % 2;
    }

    private CopyPump(
      BiConsumer<ByteBuffer, CompletionHandler<Integer, Object>> read,
      TriConsumer<ByteBuffer, Integer, CompletionHandler<Integer, Object>> write,
      CompletionHandler<Integer, Object> handler) {

        this.read = read;
        this.write = write;
        this.handler = handler;
    }

    public static void run(
      BiConsumer<ByteBuffer, CompletionHandler<Integer, Object>> read,
      TriConsumer<ByteBuffer, Integer, CompletionHandler<Integer, Object>> write,
      CompletionHandler<Integer, Object> handler) {
        new CopyPump(read, write, handler).start();
    }

    private void start() {
        pending.set(1);
        logger.info("start read");
        read.accept(buffers[getReadBufferIx()], readCompletionHandler);
    }

    private void nextStep() {
        if (pending.decrementAndGet() != 0) {
            // asynchronous operation is still pending
            return;
        }
        logger.info("nextStep");
        // No pending operation, can proceed
        if (readException != null || writeException != null) {
            logger.info("At least one exception pending, ending");
            CompositeThrowable exc = CompositeThrowable.make(readException, writeException);
            handler.failed(exc, null);
            return;
        }
        if (lastWrite) {
            logger.info("Ended last write, completing...");
            handler.completed(writePosition, null);
            return;
        }
        flipBuffers();
        buffers[getReadBufferIx()].clear();
        buffers[getWriteBufferIx()].flip();
        if (readCompleted) {
            lastWrite = true;
        }
        pending.set(lastWrite ? 1 : 2);
        if (!readCompleted) {
            logger.info("Starting next read");
            startRead();
        }
        logger.info("Starting next writes");
        startWrite();
    }

    private void startRead() {
        try {
            read.accept(buffers[getReadBufferIx()], readCompletionHandler);
        } catch (Throwable e) {
            readCompletionHandler.failed(e, null);
        }
    }

    private void startWrite() {
        try {
            write.accept(buffers[getWriteBufferIx()], writePosition, writeCompletionHandler);
        } catch (Throwable e) {
            writeCompletionHandler.failed(e, null);
        }
    }

    private final CompletionHandler<Integer, Object> readCompletionHandler =
      new CompletionHandler<Integer, Object>() {
          @Override
          public void completed(Integer result, Object attachment) {
              if (result == -1) {
                  readCompleted = true;
              }
              logger.info("Read completed with result={}", result);
              nextStep();
          }

          @Override
          public void failed(Throwable exc, Object attachment) {
              logger.warn("read failed");
              readException = exc;
              nextStep();
          }
      };

    private final CompletionHandler<Integer, Object> writeCompletionHandler =
      new CompletionHandler<Integer, Object>() {
          @Override
          public void completed(Integer result, Object attachment) {
              writePosition += result;
              logger.info("Write completed with result={}, writePosition={}", result, writePosition);
              nextStep();
          }

          @Override
          public void failed(Throwable exc, Object attachment) {
              logger.warn("write failed");
              writeException = exc;
              nextStep();
          }
      };
}
