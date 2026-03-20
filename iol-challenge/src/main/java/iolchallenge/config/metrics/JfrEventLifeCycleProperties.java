package iolchallenge.config.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jfr.pinned-vts.lifecycle", ignoreInvalidFields = true)
public record JfrEventLifeCycleProperties(
    Duration streamEventMaxAge) {
}
