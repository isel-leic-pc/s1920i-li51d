package pt.isel.pc.examples.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class FetchAndSave {

    private final String host;
    private final int port;
    private final String fileName;
    private final CompletionHandler<Void, Object> fetchAndSaveCompletionHandler;
    private final ByteBuffer copyBuffer;

    private AsynchronousSocketChannel socket;
    private AsynchronousFileChannel file;
    private int filePosition;

    public FetchAndSave(String host,
                        int port,
                        String fileName,
                        CompletionHandler<Void, Object> fetchAndSaveCompletionHandler) {

        this.host = host;
        this.port = port;
        this.fileName = fileName;
        this.fetchAndSaveCompletionHandler = fetchAndSaveCompletionHandler;
        this.copyBuffer = ByteBuffer.allocate(1);
    }

    public void start() throws IOException {
        socket = AsynchronousSocketChannel.open();
        file = AsynchronousFileChannel.open(Paths.get(fileName));
        filePosition = 0;

        socket.connect(new InetSocketAddress(host, port), null,
          new CompletionHandler<Void, Object>() {
              @Override
              public void completed(Void result, Object attachment) {
                  writeRequest();
              }

              @Override
              public void failed(Throwable exc, Object attachment) {
                  onError(exc);
              }
          });
    }

    private void onError(Throwable e) {
        // TODO - reentrancy
        fetchAndSaveCompletionHandler.failed(e, null);
    }

    private void writeRequest() {
        String requestString =
          "GET https://httpbin.org/delay/3 HTTP/1.1\r\n"
            + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
            + "\r\n";

        byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
        ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
        socket.write(requestBuffer, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                readResponse();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                onError(exc);
            }
        });
    }

    private void readResponse() {
        socket.read(copyBuffer, null,
          new CompletionHandler<Integer, Object>() {
              @Override
              public void completed(Integer result, Object attachment) {
                  writeResponse();
              }

              @Override
              public void failed(Throwable exc, Object attachment) {
                  onError(exc);
              }
          });
    }

    private void writeResponse() {
        copyBuffer.flip();
        file.write(copyBuffer, filePosition, null,
          new CompletionHandler<Integer, Object>() {
              @Override
              public void completed(Integer result, Object attachment) {
                  onWriteCompleted(result);
              }

              @Override
              public void failed(Throwable exc, Object attachment) {
                  onError(exc);
              }
          });
    }

    private void onWriteCompleted(int len) {
        copyBuffer.clear();
        filePosition += len;
        readResponse();
    }

}
