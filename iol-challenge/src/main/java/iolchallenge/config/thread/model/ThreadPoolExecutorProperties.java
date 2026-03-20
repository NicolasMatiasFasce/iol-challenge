package iolchallenge.config.thread.model;

public record ThreadPoolExecutorProperties(String name,
                                           int corePoolSize,
                                           int maximumPoolSize,
                                           int keepAliveTime,
                                           BlockingQueueProperties queue) {}
