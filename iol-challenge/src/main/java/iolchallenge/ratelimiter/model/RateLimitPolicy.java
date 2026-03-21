package iolchallenge.ratelimiter.model;

public record RateLimitPolicy(
    String method,
    String route,
    String upstreamUrl,
    int capacity,
    int refillRatePerSecond,
    String identityHeader,
    FailMode failMode)
{
}

