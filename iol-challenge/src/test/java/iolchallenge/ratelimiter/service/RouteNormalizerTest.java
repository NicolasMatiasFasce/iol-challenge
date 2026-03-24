package iolchallenge.ratelimiter.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteNormalizerTest {

    private final RouteNormalizer normalizer = new RouteNormalizer();

    @Test
    void shouldReturnRootForNullBlankOrRootPath() {
        assertEquals("/", normalizer.normalize(null));
        assertEquals("/", normalizer.normalize(""));
        assertEquals("/", normalizer.normalize("/"));
    }

    @Test
    void shouldNormalizeNumericAndUuidSegments() {
        String first = normalizer.normalize("/users/123/orders/550e8400-e29b-41d4-a716-446655440000");
        String second = normalizer.normalize("/users/999/orders/11111111-1111-1111-1111-111111111111");

        assertEquals("/users/{id}/orders/{id}", first);
        assertEquals(first, second);
    }

    @Test
    void shouldKeepStaticRouteSegmentsUntouched() {
        assertEquals("/health/check", normalizer.normalize("/health/check"));
    }
}

