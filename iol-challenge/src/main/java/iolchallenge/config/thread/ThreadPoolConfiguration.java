package iolchallenge.config.thread;

import iolchallenge.config.server.factory.JettyThreadPoolFactory;
import iolchallenge.config.server.model.JettyThreadPoolProperties;
import iolchallenge.config.thread.factory.ThreadPoolExecutorFactory;
import iolchallenge.config.thread.model.ExecutorsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.GenericWebApplicationContext;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.Optional.ofNullable;

@Configuration
public class ThreadPoolConfiguration {

    private static final String THREADS = ".threads";
    private static final String IDLE = ".idle";
    private static final String BUSY = ".busy";

    @Bean("jettyThreadPool")
    public ThreadPool jettyThreadPool(JettyThreadPoolProperties jettyThreadPoolProperties,
                                      JettyThreadPoolFactory serverThreadPoolFactory,
                                      MeterRegistry registry) {
        ThreadPool jettyThreadPool = serverThreadPoolFactory.create(jettyThreadPoolProperties);

        if (jettyThreadPool instanceof ExecutorThreadPool pool) {
            registerExecutorThreadPoolMetrics("thread.pool.jetty", registry, pool);
        } else if (jettyThreadPool instanceof QueuedThreadPool pool) {
            registerQueuedThreadPool("thread.pool.jetty", registry, pool);
        }
        return jettyThreadPool;
    }

    @Autowired
    public void registerExecutors(GenericWebApplicationContext applicationContext,
                                  ExecutorsProperties threadPoolExecutorsProperties,
                                  ThreadPoolExecutorFactory threadPoolExecutorFactory,
                                  MeterRegistry registry
    ) {
        ofNullable(threadPoolExecutorsProperties.executors()).orElse(Map.of()).values()
                .forEach(threadPoolExecutorProperties -> {
                    var name = toBeanName(threadPoolExecutorProperties.name());
                    var executor = threadPoolExecutorFactory.create(threadPoolExecutorProperties);
                    registerThreadPoolExecutor(name, registry, executor);
                    applicationContext.registerBean(
                            name,
                            ThreadPoolExecutor.class,
                            () -> executor,
                            bd -> bd.setAutowireCandidate(true)
                    );
                });
    }

    private String toBeanName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "threadPoolExecutor";
        }

        String compact = rawName.replaceAll("[^A-Za-z0-9]", "");
        if (compact.isBlank()) {
            return "threadPoolExecutor";
        }
        return Character.toLowerCase(compact.charAt(0)) + compact.substring(1);
    }

    private void registerExecutorThreadPoolMetrics(String name, MeterRegistry registry, ExecutorThreadPool pool) {
        registry.gauge(name + THREADS, pool, ExecutorThreadPool::getThreads);
        registry.gauge(name + IDLE, pool, ExecutorThreadPool::getIdleThreads);
        registry.gauge(name + BUSY, pool, p -> p.getThreads() - p.getIdleThreads());
        registry.gauge(name + ".max", pool, ExecutorThreadPool::getMaxThreads);
        registry.gauge(name + ".min", pool, ExecutorThreadPool::getMinThreads);
    }

    private void registerThreadPoolExecutor(String name, MeterRegistry registry, ThreadPoolExecutor pool) {
        registry.gauge(name + THREADS, pool, ThreadPoolExecutor::getPoolSize);
        registry.gauge(name + IDLE, pool, p -> p.getPoolSize() - p.getActiveCount());
        registry.gauge(name + BUSY, pool, ThreadPoolExecutor::getActiveCount);
        registry.gauge(name + ".max", pool, ThreadPoolExecutor::getMaximumPoolSize);
        registry.gauge(name + ".min", pool, ThreadPoolExecutor::getCorePoolSize);
    }

    private void registerQueuedThreadPool(String name, MeterRegistry registry, QueuedThreadPool pool) {
        registry.gauge(name + THREADS, pool, QueuedThreadPool::getThreads);
        registry.gauge(name + IDLE, pool, QueuedThreadPool::getIdleThreads);
        registry.gauge(name + BUSY, pool, p -> p.getThreads() - p.getIdleThreads());
        registry.gauge(name + ".queue.size", pool, QueuedThreadPool::getQueueSize);
    }
}
