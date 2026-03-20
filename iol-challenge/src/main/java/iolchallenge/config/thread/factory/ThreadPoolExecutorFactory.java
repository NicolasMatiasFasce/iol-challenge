package iolchallenge.config.thread.factory;

import iolchallenge.config.thread.model.ThreadPoolExecutorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ThreadPoolExecutorFactory {
    private final BlockingQueueFactory blockingQueueFactory;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolExecutorFactory.class);

    @Autowired
    public ThreadPoolExecutorFactory(BlockingQueueFactory blockingQueueFactory) {
        this.blockingQueueFactory = blockingQueueFactory;
    }

    public ThreadPoolExecutor create(ThreadPoolExecutorProperties properties) {
        return new ThreadPoolExecutor(
                properties.corePoolSize(),
                properties.maximumPoolSize(),
                properties.keepAliveTime(),
                TimeUnit.MILLISECONDS,
                blockingQueueFactory.create(properties.queue()),
                getThreadFactory(properties)
        );
    }

    private static ThreadFactory getThreadFactory(ThreadPoolExecutorProperties properties) {
        AtomicInteger sequence = new AtomicInteger(1);
        String prefix = properties.name() + "-thread-";
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("[{}] Error processing event", properties.name(), e));
            return thread;
        };
    }
}
