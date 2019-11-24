package pt.isel.pc;

import java.util.concurrent.Callable;

import static org.junit.Assert.fail;

public class TestUtils {

    public static <E, T> E expect(Class<E> exceptionClass, Callable<T> callable) {
        try {
            callable.call();
            fail("An exception was expected and didn't occur");
            // will never reach this point
            return null;
        } catch (Exception e) {
            if (!exceptionClass.isInstance(e)) {
                fail(String.format("An exception of type '%s' was expected"
                    + "and instead an exception of type '%s' ocurred",
                  exceptionClass.getName(),
                  e.getClass().getName()));
            }
            return exceptionClass.cast(e);
        }
    }
}
