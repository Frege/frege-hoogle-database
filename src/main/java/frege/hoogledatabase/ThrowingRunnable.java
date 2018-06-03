package frege.hoogledatabase;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
