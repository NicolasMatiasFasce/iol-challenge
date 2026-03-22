package iolchallenge.ratelimiter.service;

import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitOutcome;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterOrchestratorService.class);

    private final RateLimiterProperties properties;
    private final RateLimitRulesStore rulesStore;
    private final IdentityExtractor identityExtractor;
    private final RouteNormalizer routeNormalizer;
    private final RateLimitDecisionService decisionService;

    /**
     * Orquesta la evaluacion completa de cuota para una request entrante.
     */
    public RateLimiterOrchestratorService(
        RateLimiterProperties properties,
        RateLimitRulesStore rulesStore,
        IdentityExtractor identityExtractor,
        RouteNormalizer routeNormalizer,
        RateLimitDecisionService decisionService)
    {
        this.properties = properties;
        this.rulesStore = rulesStore;
        this.identityExtractor = identityExtractor;
        this.routeNormalizer = routeNormalizer;
        this.decisionService = decisionService;
    }

    /**
     * Resuelve politica, identidad, clave de cuota y decision final (allow/reject).
     */
    public RateLimitOutcome evaluate(HttpServletRequest request) {
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
        if (decision.allowed()) {
            LOGGER.debug("Request allowed identity={} method={} route={} remaining={} degraded={}",
                identity, request.getMethod(), normalizedRoute, decision.remaining(), decision.degraded());
        } else {
            LOGGER.debug("Request throttled identity={} method={} route={} limit={} remaining={} retryAfterSeconds={} degraded={}",
                identity, request.getMethod(), normalizedRoute, decision.limit(), decision.remaining(),
                decision.retryAfterSeconds(), decision.degraded());
        }

        return new RateLimitOutcome(decision.allowed(), identity, normalizedRoute, forwardedPath, policy, decision);
    }

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

