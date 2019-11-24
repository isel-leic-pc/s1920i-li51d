package pt.isel.pc.examples.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public final class ParallelFetchAndSave {

    private static final Logger logger = LoggerFactory.getLogger(ParallelFetchAndSave.class);
    private final ByteBuffer buffer;
    private final AsynchronousSocketChannel socket;
    private final AsynchronousFileChannel file;
    private final String path;
    private final CompletionHandler<Void, Object> handler;

    private final static ThreadLocal<Integer> reentrancies = ThreadLocal.withInitial(() -> 0);

    private int filePosition = 0;

    private ParallelFetchAndSave(
      AsynchronousSocketChannel socket,
      AsynchronousFileChannel file,
      String path,
      CompletionHandler<Void, Object> handler) {
        this.socket = socket;
        this.file = file;
        this.path = path;
        this.handler = handler;
        this.buffer = ByteBuffer.allocate(1);
    }

    public static void run(String host,
                           int port,
                           String path,
                           String fileName,
                           CompletionHandler<Void, Object> handler)
      throws IOException {
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        socket.setOption(StandardSocketOptions.SO_SNDBUF, 16);
        AsynchronousFileChannel file = AsynchronousFileChannel.open(Paths.get(fileName),
          WRITE, CREATE);
        ParallelFetchAndSave fas = new ParallelFetchAndSave(socket, file, path, handler);
        fas.start(host, port);
    }

    public void start(String host, int port) throws IOException {
        logger.info("begin connect");
        asyncCall(() ->
          socket.connect(new InetSocketAddress(host, port), null, onConnectCompletedHandler)
        );
    }

    private final CompletionHandler<Void, Object> onConnectCompletedHandler = handler(
      "connect",
      ignored -> onConnectCompleted(),
      this::onError);

    private void onConnectCompleted() {
        tryDo(() -> {
            String requestString =
              "GET " + path + " HTTP/1.1\r\n"
                + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                + "\r\n";
            byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
            writeRequest(requestBuffer);
        });
    }

    private void writeRequest(ByteBuffer requestBuffer) {
        logger.info("begin write request");
        asyncCall(() ->
          socket.write(requestBuffer, null,
            handler("write socket",
              res -> onWriteRequestCompleted(requestBuffer),
              this::onError))
        );
    }

    private void onWriteRequestCompleted(ByteBuffer requestBuffer) {
        if (requestBuffer.position() == requestBuffer.limit()) {
            // request fully written, start read response
            readResponse();
        } else {
            // continue writing request
            writeRequest(requestBuffer);
        }
    }

    private void readResponse() {
        logger.info("begin read");
        CopyPump.run(
          (buf, handler) -> socket.read(buf, null, handler),
          (buf, filePosition, handler) -> write(buf, filePosition, handler),
          onCopyCompletedHandler);
    }

    private void write(ByteBuffer buf, int filePosition,
                       CompletionHandler<Integer, Object> handler) {
        if (filePosition > 10) {
            handler.failed(new Exception("to test error handler"), null);
        }
        file.write(buf, filePosition, null, handler);
    }

    private final CompletionHandler<Integer, Object> onCopyCompletedHandler = handler(
      "copy",
      it -> completeWithSuccess(),
      this::onError);

    private void completeWithSuccess() {
        try {
            socket.close();
            file.close();
            handler.completed(null, null);
        } catch (IOException e) {
            closeSilently(socket);
            closeSilently(file);
            handler.failed(e, null);
        }
    }

    // utils below

    private void tryDo(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            closeSilently(socket);
            closeSilently(file);
            handler.failed(e, null);
        }
    }

    private void onError(Throwable e) {
        closeSilently(socket);
        closeSilently(file);
        handler.failed(e, null);
    }

    private void asyncCall(Runnable runnable) {
        reentrancies.set(1);
        try {
            runnable.run();
        } finally {
            reentrancies.set(0);
        }
    }

    private void checkReentrancy() {
        int i = reentrancies.get();
        if (i > 0) {
            logger.warn("Reentrancy detected: {}", i);
        }
    }

    private static void closeSilently(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            logger.warn("Expected IOException while closing {}", closeable);
            // ignoring exception
        }
    }

    private <T> CompletionHandler<T, Object> handler(
      String operation,
      Consumer<T> onSuccess,
      Consumer<Throwable> onError) {
        return new CompletionHandler<T, Object>() {

            @Override
            public void completed(T result, Object attachment) {
                logger.info("Completed {} with sucess: {}", operation, result);
                checkReentrancy();
                tryDo(() -> onSuccess.accept(result));
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                logger.warn("Completed {} with error: {}", operation, exc.getMessage());
                checkReentrancy();
                onError.accept(exc);
            }
        };
    }
}
