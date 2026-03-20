package iolchallenge.config.thread.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "thread-pool.notifier")
public record ThreadPoolNotifierProperties(Integer scheduleInSeconds) {}
