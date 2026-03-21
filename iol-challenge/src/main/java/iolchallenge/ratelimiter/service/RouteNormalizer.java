package iolchallenge.ratelimiter.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RouteNormalizer {

    /**
     * Normaliza una ruta HTTP a formato plantilla para reducir cardinalidad de claves.
     */
    public String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            return "/";
        }

        String normalized = Arrays.stream(rawPath.split("/"))
            .filter(segment -> !segment.isBlank())
            .map(this::normalizeSegment)
            .collect(Collectors.joining("/"));

        return "/" + normalized;
    }

    /**
     * Normaliza cada segmento reemplazando IDs numericos o UUID por `{id}`.
     */
    private String normalizeSegment(String segment) {
        if (segment.chars().allMatch(Character::isDigit)) {
            return "{id}";
        }

        try {
            UUID.fromString(segment);
            return "{id}";
        } catch (IllegalArgumentException ignored) {
            return segment;
        }
    }
}

