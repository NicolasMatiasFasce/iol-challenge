package iolchallenge.ratelimiter.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitDecisionServiceTest {

    @Test
    void shouldAllowWhenTokenBucketAllows() {
        RateLimiterProperties properties = properties(true);
        TokenBucketGateway gateway = (key, c, r) -> new TokenBucketGateway.TokenBucketResult(true, 9, 0);
        RateLimitDecisionService service = new RateLimitDecisionService(properties, gateway, new SimpleMeterRegistry());

        RateLimitDecision decision = service.evaluate("id:GET:/users/{id}", policy(FailMode.FAIL_OPEN));

        assertTrue(decision.allowed());
        assertFalse(decision.degraded());
    }

    @Test
    void shouldFailOpenOnRedisErrorWhenConfigured() {
        RateLimiterProperties properties = properties(true);
        TokenBucketGateway gateway = (key, c, r) -> {
            throw new RuntimeException("redis down");
        };
        RateLimitDecisionService service = new RateLimitDecisionService(properties, gateway, new SimpleMeterRegistry());

        RateLimitDecision decision = service.evaluate("id:GET:/users/{id}", policy(FailMode.FAIL_OPEN));

        assertTrue(decision.allowed());
        assertTrue(decision.degraded());
    }

    @Test
    void shouldFailClosedOnRedisErrorWhenConfigured() {
        RateLimiterProperties properties = properties(true);
        TokenBucketGateway gateway = (key, c, r) -> {
            throw new RuntimeException("redis down");
        };
        RateLimitDecisionService service = new RateLimitDecisionService(properties, gateway, new SimpleMeterRegistry());

        RateLimitDecision decision = service.evaluate("id:GET:/users/{id}", policy(FailMode.FAIL_CLOSED));

        assertFalse(decision.allowed());
        assertTrue(decision.degraded());
    }

    private RateLimiterProperties properties(boolean enabled) {
        return new RateLimiterProperties(
            enabled,
            "/rl",
            "./rate-limiter-rules.yaml",
            Duration.ofSeconds(30),
            10,
            5,
            "X-Api-Key",
            FailMode.FAIL_OPEN,
            "http://localhost:8081");
    }

    private RateLimitPolicy policy(FailMode failMode) {
        return new RateLimitPolicy("GET", "/users/{id}", "http://localhost:8081", 10, 5, "X-Api-Key", failMode);
    }
}

