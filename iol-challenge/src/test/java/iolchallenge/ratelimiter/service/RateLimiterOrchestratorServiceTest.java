package iolchallenge.ratelimiter.service;

import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitOutcome;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterOrchestratorServiceTest {

    @Test
    void shouldBuildDifferentQuotaKeysForDifferentEndpointsWithSameIdentity() {
        RateLimitRulesStore rulesStore = mock(RateLimitRulesStore.class);
        IdentityExtractor identityExtractor = new IdentityExtractor();
        RouteNormalizer routeNormalizer = new RouteNormalizer();
        RateLimitDecisionService decisionService = mock(RateLimitDecisionService.class);

        RateLimitPolicy usersPolicy = new RateLimitPolicy("GET", "/users/{id}", "http://upstream", 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);
        RateLimitPolicy ordersPolicy = new RateLimitPolicy("GET", "/orders/{id}", "http://upstream", 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);

        when(rulesStore.resolve(eq("GET"), eq("/users/{id}"))).thenReturn(usersPolicy);
        when(rulesStore.resolve(eq("GET"), eq("/orders/{id}"))).thenReturn(ordersPolicy);
        when(decisionService.evaluate(eq("shared-client:GET:/users/{id}"), eq(usersPolicy))).thenReturn(new RateLimitDecision(false, 10, 0, 1, false));
        when(decisionService.evaluate(eq("shared-client:GET:/orders/{id}"), eq(ordersPolicy))).thenReturn(new RateLimitDecision(false, 10, 0, 1, false));

        RateLimiterOrchestratorService service = new RateLimiterOrchestratorService(
            properties(),
            rulesStore,
            identityExtractor,
            routeNormalizer,
            decisionService);

        MockHttpServletRequest usersRequest = request("/rl/users/1");
        MockHttpServletRequest ordersRequest = request("/rl/orders/1");

        RateLimitOutcome usersOutcome = service.evaluate(usersRequest);
        RateLimitOutcome ordersOutcome = service.evaluate(ordersRequest);

        assertEquals("/users/{id}", usersOutcome.normalizedRoute());
        assertEquals("/orders/{id}", ordersOutcome.normalizedRoute());
        verify(decisionService, times(1)).evaluate(eq("shared-client:GET:/users/{id}"), eq(usersPolicy));
        verify(decisionService, times(1)).evaluate(eq("shared-client:GET:/orders/{id}"), eq(ordersPolicy));
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Api-Key", "shared-client");
        return request;
    }

    private RateLimiterProperties properties() {
        return new RateLimiterProperties(
            true,
            "/rl",
            "./rate-limiter-rules.yaml",
            Duration.ofSeconds(30),
            10,
            5,
            "X-Api-Key",
            FailMode.FAIL_OPEN,
            "http://upstream");
    }
}


