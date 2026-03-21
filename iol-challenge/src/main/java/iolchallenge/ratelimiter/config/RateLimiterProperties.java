package iolchallenge.ratelimiter.config;

import iolchallenge.ratelimiter.model.FailMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(
    boolean enabled,
    String endpointPrefix,
    String rulesPath,
    Duration refreshInterval,
    int defaultCapacity,
    int defaultRefillRatePerSecond,
    String defaultIdentityHeader,
    FailMode defaultFailMode,
    String defaultUpstreamUrl)
{
    public RateLimiterProperties {
        endpointPrefix = endpointPrefix == null || endpointPrefix.isBlank() ? "/rl" : endpointPrefix;
        rulesPath = rulesPath == null || rulesPath.isBlank() ? "./rate-limiter-rules.yaml" : rulesPath;
        refreshInterval = refreshInterval == null || refreshInterval.isNegative() || refreshInterval.isZero()
            ? Duration.ofSeconds(30)
            : refreshInterval;
        defaultCapacity = defaultCapacity <= 0 ? 100 : defaultCapacity;
        defaultRefillRatePerSecond = defaultRefillRatePerSecond <= 0 ? 50 : defaultRefillRatePerSecond;
        defaultIdentityHeader = defaultIdentityHeader == null || defaultIdentityHeader.isBlank() ? "X-Api-Key" : defaultIdentityHeader;
        defaultFailMode = defaultFailMode == null ? FailMode.FAIL_OPEN : defaultFailMode;
        defaultUpstreamUrl = defaultUpstreamUrl == null ? "" : defaultUpstreamUrl.trim();
    }
}

