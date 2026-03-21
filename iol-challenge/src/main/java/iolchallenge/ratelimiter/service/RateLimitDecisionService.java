package iolchallenge.ratelimiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitDecision;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimitDecisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitDecisionService.class);

    private final RateLimiterProperties properties;
    private final TokenBucketGateway tokenBucketGateway;
    private final Counter allowedCounter;
    private final Counter limitedCounter;

    /**
     * Inicializa el servicio de decision de cuota y registra las metricas principales.
     */
    public RateLimitDecisionService(
        RateLimiterProperties properties,
        TokenBucketGateway tokenBucketGateway,
        MeterRegistry meterRegistry)
    {
        this.properties = properties;
        this.tokenBucketGateway = tokenBucketGateway;
        this.allowedCounter = meterRegistry.counter("rate_limiter_requests_allowed_total");
        this.limitedCounter = meterRegistry.counter("rate_limiter_requests_limited_total");
    }

    /**
     * Evalua si una request puede continuar segun la politica de cuota.
     *
     * <p>Cuando el backend de cuota falla, aplica la degradacion configurada
     * en la politica (fail-open o fail-closed).</p>
     */
    public RateLimitDecision evaluate(String quotaKey, RateLimitPolicy policy) {
        if (!properties.enabled()) {
            LOGGER.debug("Rate limiter bypass enabled=false quotaKey={}", quotaKey);
            allowedCounter.increment();
            return new RateLimitDecision(true, policy.capacity(), policy.capacity(), 0, false);
        }

        try {
            TokenBucketGateway.TokenBucketResult result = tokenBucketGateway.consume(quotaKey, policy.capacity(), policy.refillRatePerSecond());
            if (result.allowed()) {
                allowedCounter.increment();
            } else {
                limitedCounter.increment();
            }

            return new RateLimitDecision(
                result.allowed(),
                policy.capacity(),
                result.remaining(),
                result.retryAfterSeconds(),
                false);
        } catch (RuntimeException ex) {
            if (policy.failMode() == FailMode.FAIL_OPEN) {
                LOGGER.warn("Rate limiter degraded fail-open quotaKey={} message={}", quotaKey, ex.getMessage());
                allowedCounter.increment();
                return new RateLimitDecision(true, policy.capacity(), policy.capacity(), 0, true);
            }

            LOGGER.warn("Rate limiter degraded fail-closed quotaKey={} message={}", quotaKey, ex.getMessage());
            limitedCounter.increment();
            return new RateLimitDecision(false, policy.capacity(), 0, 1, true);
        }
    }
}

