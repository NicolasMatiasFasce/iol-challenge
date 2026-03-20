package iolchallenge.config.server.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server.jetty", ignoreInvalidFields = true)
public record JettyProperties(Integer port, JettyThreadPoolProperties threadPool) {}
