package pt.isel.pc;

public class UnexpectedExceptionError extends RuntimeException {

    public UnexpectedExceptionError(Exception e) {
        super("Unexpected exception", e);
    }
}
