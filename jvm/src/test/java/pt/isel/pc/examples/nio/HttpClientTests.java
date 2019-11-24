package pt.isel.pc.examples.nio;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class HttpClientTests {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientTests.class);

    @Test
    public void simpleGet() throws InterruptedException {
        Semaphore done = new Semaphore(0);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://httpbin.org/delay/3"))
          .build();

        CompletableFuture<String> stringCompletableFuture = client.sendAsync(
          request, HttpResponse.BodyHandlers.ofString())
          .thenApply(resp -> resp.body());

        stringCompletableFuture.thenAccept(s -> {
            logger.info(s);
            done.release();
        });

        done.acquire();
    }
}
