package iolchallenge.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RateLimitRule(
    String name,
    String method,
    String route,
    String upstreamUrl,
    Integer capacity,
    Integer refillRatePerSecond,
    String identityHeader,
    String failMode)
{
}

