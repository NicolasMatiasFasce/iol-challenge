package iolchallenge.config.server.model;

import iolchallenge.config.thread.model.ThreadPoolExecutorProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server.jetty.thread-pool", ignoreInvalidFields = true)
public record JettyThreadPoolProperties(ThreadPoolExecutorProperties executor, JettyQueuedThreadPoolProperties queued) {}
