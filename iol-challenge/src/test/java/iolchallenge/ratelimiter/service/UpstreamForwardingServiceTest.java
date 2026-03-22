package iolchallenge.ratelimiter.service;

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

class UpstreamForwardingServiceTest {

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
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("upstream-ok").addHeader("Content-Type", "text/plain"));

        UpstreamForwardingService service = new UpstreamForwardingService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rl/users/123");
        request.setQueryString("status=active");
        request.addHeader("X-Api-Key", "client-a");
        request.addHeader("Content-Type", "application/json");
        request.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        String upstreamUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        ResponseEntity<byte[]> response = service.forward(request, upstreamUrl, "/users/123");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertArrayEquals("upstream-ok".getBytes(StandardCharsets.UTF_8), response.getBody());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/users/123?status=active", recordedRequest.getPath());
        assertEquals("client-a", recordedRequest.getHeader("X-Api-Key"));
        assertEquals("{\"a\":1}", recordedRequest.getBody().readUtf8());
    }
}

