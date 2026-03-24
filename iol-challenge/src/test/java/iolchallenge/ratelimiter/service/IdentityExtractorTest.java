package iolchallenge.ratelimiter.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityExtractorTest {

    private final IdentityExtractor extractor = new IdentityExtractor();

    @Test
    void shouldUseConfiguredIdentityHeaderWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "client-abc");
        request.addHeader("X-Forwarded-For", "200.1.1.1");
        request.setRemoteAddr("10.0.0.1");

        String identity = extractor.extract(request, "X-Api-Key");

        assertEquals("client-abc", identity);
    }

    @Test
    void shouldFallbackToFirstXForwardedForValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "200.1.1.1, 10.0.0.1");
        request.setRemoteAddr("10.0.0.2");

        String identity = extractor.extract(request, "X-Api-Key");

        assertEquals("200.1.1.1", identity);
    }

    @Test
    void shouldFallbackToRemoteAddressWhenNoHeaderIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");

        String identity = extractor.extract(request, "X-Api-Key");

        assertEquals("10.10.10.10", identity);
    }
}

