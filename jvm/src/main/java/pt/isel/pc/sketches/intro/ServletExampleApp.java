package pt.isel.pc.sketches.intro;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.utils.Utils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ServletExampleApp {

    private static final Logger log = LoggerFactory.getLogger(ServletExampleApp.class);

    public static void main(String[] args) throws Exception {
        log.info("main started");
        SimpleCounter counter = new SimpleCounter();
        SimpleServlet servlet = new SimpleServlet(counter);
        runServer(servlet, 8081);
    }

    private static class SimpleServlet extends HttpServlet {

        private final SimpleCounter counter;
        // private String path;

        public SimpleServlet(SimpleCounter counter) {
            this.counter = counter;
        }

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            log.info("Start request processing, path = {}", req.getPathInfo());
            String path = req.getPathInfo();
            if("/favicon.ico".equals(req.getPathInfo())) {
                resp.setStatus(404);
                return;
            }
            long count = counter.increment();
            // Utils.sleep(5000, TimeUnit.MILLISECONDS);
            log.info("End request processing, path = {}", req.getPathInfo());
            Utils.writeToHttpServletResponse(resp,
                    String.format("Hello Web, path = %s, count = %s", path, count));
        }
    }

    private static void runServer(HttpServlet servlet, int port) throws Exception {
        Server server = new Server(port);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(new ServletHolder(servlet), "/*");
        server.start();
        log.info("Server started on port {}", port);
        server.join();
        log.info("Server stopped");
    }

}
