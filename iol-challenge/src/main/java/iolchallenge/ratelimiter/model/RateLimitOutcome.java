package iolchallenge.ratelimiter.model;

public record RateLimitOutcome(
    boolean allowed,
    String identity,
    String normalizedRoute,
    String forwardedPath,
    RateLimitPolicy policy,
    RateLimitDecision decision)
{
}

