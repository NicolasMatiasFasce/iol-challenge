package iolchallenge.ratelimiter.controller;

import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import iolchallenge.ratelimiter.service.IdentityExtractor;
import iolchallenge.ratelimiter.service.RateLimitDecisionService;
import iolchallenge.ratelimiter.service.RateLimitRulesStore;
import iolchallenge.ratelimiter.service.RouteNormalizer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterProxyControllerTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void shouldForwardAllowedRequestPreservingMethodPathQueryHeadersAndBody() throws Exception {
        String upstreamUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        RateLimitPolicy policy = new RateLimitPolicy("POST", "/users/{id}", upstreamUrl, 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);

        RateLimitRulesStore rulesStore = mock(RateLimitRulesStore.class);
        when(rulesStore.resolve(eq("POST"), eq("/users/{id}"))).thenReturn(policy);

        RateLimitDecisionService decisionService = mock(RateLimitDecisionService.class);
        when(decisionService.evaluate(anyString(), eq(policy))).thenReturn(new RateLimitDecision(true, 10, 9, 0, false));

        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("upstream-ok").addHeader("Content-Type", "text/plain"));

        RateLimiterProxyController controller = new RateLimiterProxyController(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            properties(true),
            rulesStore,
            decisionService,
            new IdentityExtractor(),
            new RouteNormalizer());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rl/users/123");
        request.setQueryString("status=active");
        request.addHeader("X-Api-Key", "client-a");
        request.addHeader("Content-Type", "application/json");
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<byte[]> response = controller.proxy(request, body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertArrayEquals("upstream-ok".getBytes(StandardCharsets.UTF_8), response.getBody());
        assertNull(response.getHeaders().getFirst("X-RateLimit-Limit"));

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/users/123?status=active", recordedRequest.getPath());
        assertEquals("client-a", recordedRequest.getHeader("X-Api-Key"));
        assertEquals("{\"a\":1}", recordedRequest.getBody().readUtf8());
    }

    @Test
    void shouldRejectWith429AndRateLimitHeadersWithoutForwarding() {
        String upstreamUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        RateLimitPolicy policy = new RateLimitPolicy("GET", "/orders/{id}", upstreamUrl, 20, 10, "X-Api-Key", FailMode.FAIL_OPEN);

        RateLimitRulesStore rulesStore = mock(RateLimitRulesStore.class);
        when(rulesStore.resolve(eq("GET"), eq("/orders/{id}"))).thenReturn(policy);

        RateLimitDecisionService decisionService = mock(RateLimitDecisionService.class);
        when(decisionService.evaluate(anyString(), eq(policy))).thenReturn(new RateLimitDecision(false, 20, 0, 2, false));

        RateLimiterProxyController controller = new RateLimiterProxyController(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            properties(true),
            rulesStore,
            decisionService,
            new IdentityExtractor(),
            new RouteNormalizer());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rl/orders/1");
        request.addHeader("X-Api-Key", "client-b");

        ResponseEntity<byte[]> response = controller.proxy(request, null);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("20", response.getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("0", response.getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("2", response.getHeaders().getFirst("X-RateLimit-Retry-After"));
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    void shouldSupportTwoDifferentUpstreamsWithoutCodeChanges() throws Exception {
        MockWebServer secondUpstream = new MockWebServer();
        secondUpstream.start();
        try {
            String usersUpstream = mockWebServer.url("/").toString().replaceAll("/$", "");
            String ordersUpstream = secondUpstream.url("/").toString().replaceAll("/$", "");

            RateLimitPolicy usersPolicy = new RateLimitPolicy("GET", "/users/{id}", usersUpstream, 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);
            RateLimitPolicy ordersPolicy = new RateLimitPolicy("GET", "/orders/{id}", ordersUpstream, 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);

            RateLimitRulesStore rulesStore = mock(RateLimitRulesStore.class);
            when(rulesStore.resolve(eq("GET"), eq("/users/{id}"))).thenReturn(usersPolicy);
            when(rulesStore.resolve(eq("GET"), eq("/orders/{id}"))).thenReturn(ordersPolicy);

            RateLimitDecisionService decisionService = mock(RateLimitDecisionService.class);
            when(decisionService.evaluate(anyString(), any())).thenReturn(new RateLimitDecision(true, 10, 9, 0, false));

            mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("users-ok"));
            secondUpstream.enqueue(new MockResponse().setResponseCode(200).setBody("orders-ok"));

            RateLimiterProxyController controller = new RateLimiterProxyController(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                properties(true),
                rulesStore,
                decisionService,
                new IdentityExtractor(),
                new RouteNormalizer());

            MockHttpServletRequest usersRequest = new MockHttpServletRequest("GET", "/rl/users/1");
            usersRequest.addHeader("X-Api-Key", "shared-client");
            MockHttpServletRequest ordersRequest = new MockHttpServletRequest("GET", "/rl/orders/1");
            ordersRequest.addHeader("X-Api-Key", "shared-client");

            ResponseEntity<byte[]> usersResponse = controller.proxy(usersRequest, null);
            ResponseEntity<byte[]> ordersResponse = controller.proxy(ordersRequest, null);

            assertEquals(HttpStatus.OK, usersResponse.getStatusCode());
            assertEquals(HttpStatus.OK, ordersResponse.getStatusCode());
            assertEquals("users-ok", new String(usersResponse.getBody(), StandardCharsets.UTF_8));
            assertEquals("orders-ok", new String(ordersResponse.getBody(), StandardCharsets.UTF_8));
            assertEquals("/users/1", mockWebServer.takeRequest().getPath());
            assertEquals("/orders/1", secondUpstream.takeRequest().getPath());
        } finally {
            secondUpstream.shutdown();
        }
    }

    @Test
    void shouldUseDifferentQuotaKeysForDifferentEndpointsWithSameIdentity() {
        String upstreamUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        RateLimitPolicy usersPolicy = new RateLimitPolicy("GET", "/users/{id}", upstreamUrl, 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);
        RateLimitPolicy ordersPolicy = new RateLimitPolicy("GET", "/orders/{id}", upstreamUrl, 10, 5, "X-Api-Key", FailMode.FAIL_OPEN);

        RateLimitRulesStore rulesStore = mock(RateLimitRulesStore.class);
        when(rulesStore.resolve(eq("GET"), eq("/users/{id}"))).thenReturn(usersPolicy);
        when(rulesStore.resolve(eq("GET"), eq("/orders/{id}"))).thenReturn(ordersPolicy);

        RateLimitDecisionService decisionService = mock(RateLimitDecisionService.class);
        when(decisionService.evaluate(anyString(), any())).thenReturn(new RateLimitDecision(false, 10, 0, 1, false));

        RateLimiterProxyController controller = new RateLimiterProxyController(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
            properties(true),
            rulesStore,
            decisionService,
            new IdentityExtractor(),
            new RouteNormalizer());

        MockHttpServletRequest usersRequest = new MockHttpServletRequest("GET", "/rl/users/1");
        usersRequest.addHeader("X-Api-Key", "shared-client");
        MockHttpServletRequest ordersRequest = new MockHttpServletRequest("GET", "/rl/orders/1");
        ordersRequest.addHeader("X-Api-Key", "shared-client");

        controller.proxy(usersRequest, null);
        controller.proxy(ordersRequest, null);

        verify(decisionService, times(1)).evaluate(eq("shared-client:GET:/users/{id}"), eq(usersPolicy));
        verify(decisionService, times(1)).evaluate(eq("shared-client:GET:/orders/{id}"), eq(ordersPolicy));
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
            mockWebServer.url("/").toString().replaceAll("/$", ""));
    }
}


