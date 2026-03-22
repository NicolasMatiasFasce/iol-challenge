package iolchallenge.ratelimiter.controller;

import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitOutcome;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import iolchallenge.ratelimiter.service.RateLimiterOrchestratorService;
import iolchallenge.ratelimiter.service.UpstreamForwardingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterProxyControllerTest {

    @Test
    void shouldRejectWith429AndRateLimitHeadersWithoutForwarding() {
        RateLimiterOrchestratorService orchestratorService = mock(RateLimiterOrchestratorService.class);
        UpstreamForwardingService forwardingService = mock(UpstreamForwardingService.class);

        RateLimitPolicy policy = new RateLimitPolicy("GET", "/orders/{id}", "http://upstream", 20, 10, "X-Api-Key", FailMode.FAIL_OPEN);
        RateLimitDecision decision = new RateLimitDecision(false, 20, 0, 2, false);
        RateLimitOutcome outcome = new RateLimitOutcome(false, "client-b", "/orders/{id}", "/orders/1", policy, decision);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rl/orders/1");
        when(orchestratorService.evaluate(eq(request))).thenReturn(outcome);

        RateLimiterProxyController controller = new RateLimiterProxyController(orchestratorService, forwardingService);
        ResponseEntity<byte[]> response = controller.proxy(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("20", response.getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("0", response.getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("2", response.getHeaders().getFirst("X-RateLimit-Retry-After"));
        verify(forwardingService, never()).forward(eq(request), eq("http://upstream"), eq("/orders/1"));
    }

    @Test
    void shouldForwardWhenAllowed() {
        RateLimiterOrchestratorService orchestratorService = mock(RateLimiterOrchestratorService.class);
        UpstreamForwardingService forwardingService = mock(UpstreamForwardingService.class);

        RateLimitPolicy policy = new RateLimitPolicy("GET", "/users/{id}", "http://upstream", 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);
        RateLimitDecision decision = new RateLimitDecision(true, 10, 9, 0, false);
        RateLimitOutcome outcome = new RateLimitOutcome(true, "client-a", "/users/{id}", "/users/1", policy, decision);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rl/users/1");
        when(orchestratorService.evaluate(eq(request))).thenReturn(outcome);
        when(forwardingService.forward(eq(request), eq("http://upstream"), eq("/users/1")))
            .thenReturn(ResponseEntity.status(HttpStatus.OK).body("ok".getBytes()));

        RateLimiterProxyController controller = new RateLimiterProxyController(orchestratorService, forwardingService);
        ResponseEntity<byte[]> response = controller.proxy(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals("ok".getBytes(), response.getBody());
        verify(forwardingService).forward(eq(request), eq("http://upstream"), eq("/users/1"));
    }
}


