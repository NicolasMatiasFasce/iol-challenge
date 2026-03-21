package iolchallenge.ratelimiter.controller;

import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import iolchallenge.ratelimiter.service.IdentityExtractor;
import iolchallenge.ratelimiter.service.RateLimitDecisionService;
import iolchallenge.ratelimiter.service.RateLimitRulesStore;
import iolchallenge.ratelimiter.service.RouteNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

@RestController
@RequestMapping("${rate-limiter.endpoint-prefix:/rl}")
public class RateLimiterProxyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterProxyController.class);

    private final HttpClient httpClient;
    private final RateLimiterProperties properties;
    private final RateLimitRulesStore rulesStore;
    private final RateLimitDecisionService decisionService;
    private final IdentityExtractor identityExtractor;
    private final RouteNormalizer routeNormalizer;

    /**
     * Construye el controlador intermedio que aplica rate limiting antes de reenviar.
     */
    public RateLimiterProxyController(
        HttpClient rateLimiterHttpClient,
        RateLimiterProperties properties,
        RateLimitRulesStore rulesStore,
        RateLimitDecisionService decisionService,
        IdentityExtractor identityExtractor,
        RouteNormalizer routeNormalizer)
    {
        this.httpClient = rateLimiterHttpClient;
        this.properties = properties;
        this.rulesStore = rulesStore;
        this.decisionService = decisionService;
        this.identityExtractor = identityExtractor;
        this.routeNormalizer = routeNormalizer;
    }

    /**
     * Punto unico de entrada del middleware: decide cuota y luego reenvia o responde 429.
     */
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String forwardedPath = extractForwardedPath(request);
        String normalizedRoute = routeNormalizer.normalize(forwardedPath);

        LOGGER.debug("Rate limiter request received method={} rawPath={} normalizedRoute={}",
            request.getMethod(), request.getRequestURI(), normalizedRoute);

        RateLimitPolicy policy = rulesStore.resolve(request.getMethod(), normalizedRoute);
        String identity = identityExtractor.extract(request, policy.identityHeader());
        String quotaKey = identity + ":" + request.getMethod().toUpperCase() + ":" + normalizedRoute;

        LOGGER.debug("Rate limiter policy resolved method={} route={} upstream={} capacity={} refill={} identityHeader={} failMode={}",
            policy.method(), policy.route(), policy.upstreamUrl(), policy.capacity(), policy.refillRatePerSecond(),
            policy.identityHeader(), policy.failMode());

        RateLimitDecision decision = decisionService.evaluate(quotaKey, policy);
        if (!decision.allowed()) {
            LOGGER.debug("Request throttled identity={} method={} route={} limit={} remaining={} retryAfterSeconds={} degraded={}",
                identity, request.getMethod(), normalizedRoute, decision.limit(), decision.remaining(),
                decision.retryAfterSeconds(), decision.degraded());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(decision.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(decision.remaining()))
                .header("X-RateLimit-Retry-After", String.valueOf(decision.retryAfterSeconds()))
                .body(new byte[0]);
        }

        LOGGER.debug("Request allowed identity={} method={} route={} remaining={} degraded={}",
            identity, request.getMethod(), normalizedRoute, decision.remaining(), decision.degraded());

        return forward(request, policy.upstreamUrl(), forwardedPath);
    }

    /**
     * Reenvia la request al upstream preservando metodo, payload y headers relevantes.
     */
    private ResponseEntity<byte[]> forward(HttpServletRequest request, String upstreamUrl, String forwardedPath) {
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

    /**
     * Construye la URI final de destino combinando upstream, path y query string.
     */
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

    /**
     * Copia headers de entrada, excluyendo headers hop-by-hop o calculados por el cliente HTTP.
     */
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

    /**
     * Propaga headers del upstream hacia la respuesta del middleware, filtrando hop-by-hop.
     */
    private void copyResponseHeaders(HttpResponse<byte[]> upstreamResponse, ResponseEntity.BodyBuilder responseBuilder) {
        upstreamResponse.headers().map().forEach((name, values) -> {
            if ("transfer-encoding".equalsIgnoreCase(name) || "connection".equalsIgnoreCase(name)) {
                return;
            }
            values.forEach(value -> responseBuilder.header(name, value));
        });
    }

    /**
     * Remueve el prefijo del middleware para obtener el path real a reenviar.
     */
    private String extractForwardedPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = properties.endpointPrefix();
        if (!requestUri.startsWith(prefix)) {
            return requestUri;
        }

        String path = requestUri.substring(prefix.length());
        return path.isBlank() ? "/" : path;
    }
}


