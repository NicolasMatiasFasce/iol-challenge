package iolchallenge.ratelimiter.service;

public interface TokenBucketGateway {
    TokenBucketResult consume(String key, int capacity, int refillRatePerSecond);

    record TokenBucketResult(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}

