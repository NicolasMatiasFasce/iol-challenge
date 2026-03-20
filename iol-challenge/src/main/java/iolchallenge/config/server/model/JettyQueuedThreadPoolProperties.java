package iolchallenge.config.server.model;

import iolchallenge.config.thread.model.BlockingQueueProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server.jetty.thread-pool.queued", ignoreInvalidFields = true)
public record JettyQueuedThreadPoolProperties(
    String name,
    int maxThreads,
    int minThreads,
    int idleTimeout,
    boolean useVirtualThreads,
    BlockingQueueProperties queue) {}
