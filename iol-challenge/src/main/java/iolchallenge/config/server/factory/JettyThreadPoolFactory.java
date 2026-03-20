package iolchallenge.config.server.factory;

import iolchallenge.config.server.model.JettyQueuedThreadPoolProperties;
import iolchallenge.config.server.model.JettyThreadPoolProperties;
import iolchallenge.config.thread.factory.BlockingQueueFactory;
import iolchallenge.config.thread.factory.ThreadPoolExecutorFactory;
import iolchallenge.config.thread.model.ThreadPoolExecutorProperties;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

@Component
public class JettyThreadPoolFactory {
    private final ThreadPoolExecutorFactory threadPoolExecutorFactory;
    private final BlockingQueueFactory blockingQueueFactory;

    @Autowired
    public JettyThreadPoolFactory(
        ThreadPoolExecutorFactory threadPoolExecutorFactory,
        BlockingQueueFactory blockingQueueFactory)
    {
        this.threadPoolExecutorFactory = threadPoolExecutorFactory;
        this.blockingQueueFactory = blockingQueueFactory;
    }

    public ThreadPool create(JettyThreadPoolProperties threadPool) {
        var executor = ofNullable(threadPool.executor()).map(this::createExecutorThreadPool);
        var queued = ofNullable(threadPool.queued()).map(this::createQueuedThreadPool);

        return executor
                .or(() -> queued)
                .orElseThrow(() -> new UnsupportedOperationException("Failed to bind server.jetty.thread-pool properties. Update your application's configuration"));
    }

    private ThreadPool createExecutorThreadPool(ThreadPoolExecutorProperties properties) {
        return new ExecutorThreadPool(threadPoolExecutorFactory.create(properties));
    }

    private ThreadPool createQueuedThreadPool(JettyQueuedThreadPoolProperties properties) {
        if (properties.useVirtualThreads()) {
            return createQueueThreadPoolWithVirtualThreads(properties);
        }
        var threadPool = new QueuedThreadPool(
                properties.maxThreads(),
                properties.minThreads(),
                properties.idleTimeout(),
                blockingQueueFactory.create(properties.queue()));
        threadPool.setName(properties.name());
        return threadPool;
    }

    private QueuedThreadPool createQueueThreadPoolWithVirtualThreads(JettyQueuedThreadPoolProperties properties) {
        // Despite the name, VirtualThreadPool does not pool virtual threads, but allows you to impose a limit on
        // the maximum number of current virtual threads, using a Semaphore.
        VirtualThreadPool virtualThreadPool = new VirtualThreadPool(properties.maxThreads());
        virtualThreadPool.setName(properties.name());
        virtualThreadPool.setTracking(true);
        virtualThreadPool.setDetailedDump(true);
        QueuedThreadPool queuedThreadPool = new QueuedThreadPool(
            properties.maxThreads(),
            properties.minThreads(),
            properties.idleTimeout(),
            blockingQueueFactory.create(properties.queue()));
        // Enabling virtual threads in QueuedThreadPool will default the number of reserved threads to zero, unless
        // the number of reserved threads is explicitly configured to a positive value.
        // Defaulting the number of reserved threads to zero ensures that the Produce-Execute-Consume mode is always
        // used, which means that virtual threads will always be used for blocking tasks.
        // https://jetty.org/docs/jetty/12/programming-guide/arch/threads.html#thread-pool-virtual-threads-queued
        queuedThreadPool.setVirtualThreadsExecutor(virtualThreadPool);
        queuedThreadPool.setName(properties.name());
        return queuedThreadPool;
    }
}
