package iolchallenge.ratelimiter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

@Service
public class UpstreamForwardingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamForwardingService.class);

    private final HttpClient httpClient;

    /**
     * Inicializa el servicio encargado de reenviar requests al upstream.
     */
    public UpstreamForwardingService(@Qualifier("rateLimiterHttpClient") HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Reenvia la request permitida preservando semantica HTTP relevante.
     */
    public ResponseEntity<byte[]> forward(HttpServletRequest request, String upstreamUrl, String forwardedPath) {
        URI upstreamUri = buildTargetUri(upstreamUrl, forwardedPath, request.getQueryString());

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(upstreamUri);
        copyRequestHeaders(request, builder);

        try {
            byte[] payload = request.getInputStream().readAllBytes();
            HttpRequest.BodyPublisher publisher = payload.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(payload);

            builder.method(request.getMethod(), publisher);

            HttpResponse<byte[]> upstreamResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            LOGGER.debug("Upstream response method={} target={} status={}", request.getMethod(), upstreamUri, upstreamResponse.statusCode());
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(upstreamResponse.statusCode());
            copyResponseHeaders(upstreamResponse, responseBuilder);
            return responseBuilder.body(upstreamResponse.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error("Upstream forward failed method={} target={} message={}", request.getMethod(), upstreamUri, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream forward failed", e);
        }
    }

    private URI buildTargetUri(String upstreamUrl, String forwardedPath, String query) {
        String base = StringUtils.trimTrailingCharacter(upstreamUrl, '/');
        String path = forwardedPath.startsWith("/") ? forwardedPath : "/" + forwardedPath;
        String raw = base + path + (query == null || query.isBlank() ? "" : "?" + query);
        try {
            return new URI(raw);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upstream URI: " + raw, e);
        }
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return;
        }

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if ("host".equalsIgnoreCase(headerName) || "content-length".equalsIgnoreCase(headerName)) {
                continue;
            }

            List<String> values = Collections.list(request.getHeaders(headerName));
            for (String value : values) {
                builder.header(headerName, value);
            }
        }
    }

    private void copyResponseHeaders(HttpResponse<byte[]> upstreamResponse, ResponseEntity.BodyBuilder responseBuilder) {
        requireNonNullElse(upstreamResponse.headers().map(), Collections.<String, List<String>>emptyMap())
            .forEach((name, values) -> {
                if ("transfer-encoding".equalsIgnoreCase(name) || "connection".equalsIgnoreCase(name)) {
                    return;
                }
                values.forEach(value -> responseBuilder.header(name, value));
            });
    }
}


