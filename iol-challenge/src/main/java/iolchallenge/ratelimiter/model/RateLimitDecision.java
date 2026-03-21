package iolchallenge.ratelimiter.model;

public record RateLimitDecision(
    boolean allowed,
    int limit,
    int remaining,
    long retryAfterSeconds,
    boolean degraded)
{
}

