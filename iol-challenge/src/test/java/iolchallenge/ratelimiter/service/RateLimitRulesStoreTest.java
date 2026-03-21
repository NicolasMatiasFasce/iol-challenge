package iolchallenge.ratelimiter.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitRulesStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFailStartupWhenNoValidSnapshotExists() {
        Path missing = tempDir.resolve("missing-rules.yaml");
        RateLimitRulesStore store = new RateLimitRulesStore(properties(missing.toString()), new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class, store::loadOnStartup);
    }

    @Test
    void shouldKeepLastValidSnapshotWhenRefreshFails() throws Exception {
        Path rules = tempDir.resolve("rules.yaml");
        Files.writeString(rules, validRules("http://localhost:8081"));

        RateLimitRulesStore store = new RateLimitRulesStore(properties(rules.toString()), new SimpleMeterRegistry());
        store.loadOnStartup();

        RateLimitPolicy before = store.resolve("GET", "/users/{id}");
        assertEquals("http://localhost:8081", before.upstreamUrl());

        Files.writeString(rules, "rules:\n  - broken: [");
        store.refresh();

        RateLimitPolicy after = store.resolve("GET", "/users/{id}");
        assertEquals("http://localhost:8081", after.upstreamUrl());
    }

    private String validRules(String upstream) {
        return """
            rules:
              - name: users
                method: GET
                route: /users/{id}
                upstreamUrl: %s
                capacity: 10
                refillRatePerSecond: 5
                identityHeader: X-Api-Key
                failMode: fail-open
            """.formatted(upstream);
    }

    private RateLimiterProperties properties(String rulesPath) {
        return new RateLimiterProperties(
            true,
            "/rl",
            rulesPath,
            Duration.ofSeconds(30),
            100,
            50,
            "X-Api-Key",
            FailMode.FAIL_OPEN,
            "http://localhost:8081");
    }
}

