package com.vocagate.desktop.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class DesktopAsyncCloser {
    private DesktopAsyncCloser() {
    }

    public static CompletableFuture<Void> closeAsync(
            AutoCloseable closeable,
            Executor executor,
            Consumer<Throwable> onError
    ) {
        if (closeable == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                closeable.close();
            } catch (Throwable throwable) {
                if (onError != null) {
                    onError.accept(throwable);
                }
            }
        }, executor);
    }
}
