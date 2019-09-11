package pt.isel.pc.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class Utils {

    private static Logger log = LoggerFactory.getLogger(Utils.class);

    public static void sleep(long duration, TimeUnit unit) {
        try {
            Thread.sleep(unit.toMillis(duration));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToHttpServletResponse(HttpServletResponse res, String s)
            throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        byte[] bytes = s.getBytes(charset);
        res.setStatus(200);
        res.setContentType("text/plain");
        res.setCharacterEncoding(charset.name());
        res.setContentLength(bytes.length);
        res.getOutputStream().write(bytes);
        res.getOutputStream().flush();
    }

}
