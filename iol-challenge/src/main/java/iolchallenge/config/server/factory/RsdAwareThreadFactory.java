package iolchallenge.config.server.factory;

import jakarta.validation.constraints.NotNull;
import java.util.concurrent.ThreadFactory;

public class RsdAwareThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate;

    public RsdAwareThreadFactory(ThreadFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        return delegate.newThread(r);
    }
}