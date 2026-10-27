import com.vocagate.desktop.app.DesktopAsyncCloser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DesktopAsyncCloserSmokeTest {
    public static void main(String[] args) throws Exception {
        testSlowCloseDoesNotBlockCaller();
        System.out.println("Desktop async closer smoke tests passed");
    }

    private static void testSlowCloseDoesNotBlockCaller() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch allowClose = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        try {
            AutoCloseable slowResource = () -> {
                closeStarted.countDown();
                allowClose.await(2, TimeUnit.SECONDS);
            };

            long startedAt = System.nanoTime();
            CompletableFuture<Void> future = DesktopAsyncCloser.closeAsync(
                    slowResource,
                    executor,
                    throwable -> errors.incrementAndGet()
            );
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(elapsedMs < 100L, "closeAsync should return immediately");
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS), "close should run on executor");
            assertTrue(!future.isDone(), "future should wait for slow close");

            allowClose.countDown();
            future.get(1, TimeUnit.SECONDS);
            assertTrue(errors.get() == 0, "no close errors should be reported");
        } finally {
            allowClose.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
