package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Semaphore;

public class SimpleFetchExampleTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleFetchExampleTest.class);

    @Test
    public void synchronousFetch() throws IOException {
        Socket socket = new Socket("httpbin.org", 80);

        String requestString =
          "GET https://httpbin.org/delay/3 HTTP/1.1\r\n"
            + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
            + "\r\n";

        byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
        OutputStream sos = socket.getOutputStream();
        InputStream sis = socket.getInputStream();
        sos.write(requestBytes);
        log.info("HTTP request sent");

        byte[] buf = new byte[4];
        OutputStream fos = new FileOutputStream("output.txt");
        while (true) {
            log.info("reading...");
            int len = sis.read(buf); // blocking operation
            if (len == -1) {
                return;
            }
            log.info("writing...");
            fos.write(buf, 0, len);  // blocking operation
        }
    }

    @Test
    public void asynchronousFetch() throws IOException, InterruptedException {
        Semaphore done = new Semaphore(0);

        fetchAndSaveAsync("httpbin.org", 80, "output.txt",
          new CompletionHandler<Void, Object>() {

              @Override
              public void completed(Void result, Object attachment) {
                  log.info("fetch and save completed with success");
                  done.release();
              }

              @Override
              public void failed(Throwable exc, Object attachment) {
                  log.info("fetch and save completed with error", exc);
                  done.release();
              }
          });

        done.acquire();
        log.info("end");
    }

    private void fetchAndSaveAsync(
      String host, int port, String fileName,
      CompletionHandler<Void, Object> fetchAndSaveCompletionHandler) throws IOException {

        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        AsynchronousFileChannel file = AsynchronousFileChannel.open(Paths.get(fileName));

        socket.connect(new InetSocketAddress(host, port), null, new CompletionHandler<Void, Object>() {
            @Override
            public void completed(Void result, Object attachment) {
                String requestString =
                  "GET https://httpbin.org/delay/3 HTTP/1.1\r\n"
                    + "User-Agent: Me\r\nHost: httpbin.org\r\nConnection: close\r\n"
                    + "\r\n";

                byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
                ByteBuffer requestBuffer = ByteBuffer.wrap(requestBytes);
                socket.write(requestBuffer, null, new CompletionHandler<Integer, Object>() {
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                fetchAndSaveCompletionHandler.failed(exc, null);
            }
        });
    }


}
