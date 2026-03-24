package iolchallenge.ratelimiter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import iolchallenge.ratelimiter.config.RateLimiterProperties;
import iolchallenge.ratelimiter.model.FailMode;
import iolchallenge.ratelimiter.model.RateLimitPolicy;
import iolchallenge.ratelimiter.model.RateLimitRule;
import iolchallenge.ratelimiter.model.RateLimitRulesFile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class RateLimitRulesStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitRulesStore.class);

    private final RateLimiterProperties properties;
    private final ObjectMapper yamlMapper;
    private final AtomicReference<Map<String, RateLimitPolicy>> snapshotByKey = new AtomicReference<>();
    private final AtomicLong lastRefreshEpochMillis = new AtomicLong(0);
    private final Counter refreshErrors;

    /**
     * Inicializa el store de reglas y registra metricas de estado del snapshot.
     */
    public RateLimitRulesStore(RateLimiterProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.refreshErrors = meterRegistry.counter("rate_limiter_rules_refresh_errors_total");
        Gauge.builder("rate_limiter_rules_snapshot_age_seconds", this, RateLimitRulesStore::snapshotAgeSeconds)
            .register(meterRegistry);
    }

    /**
     * Carga el snapshot inicial de reglas y aborta startup si no hay un estado valido.
     */
    @PostConstruct
    public void loadOnStartup() {
        refreshInternal(true);
    }

    /**
     * Ejecuta refresh periodico por polling conservando el ultimo snapshot valido ante error.
     */
    @Scheduled(fixedDelayString = "${rate-limiter.refresh-interval:30s}")
    public void refresh() {
        refreshInternal(false);
    }

    /**
     * Resuelve la politica aplicable para metodo y ruta normalizada usando fallback por comodines.
     */
    public RateLimitPolicy resolve(String method, String normalizedRoute) {
        Map<String, RateLimitPolicy> snapshot = Optional.ofNullable(snapshotByKey.get())
            .orElseThrow(() -> new IllegalStateException("Rate limit rules snapshot unavailable"));

        String routeMethodKey = key(method, normalizedRoute);
        String wildcardMethodKey = key("*", normalizedRoute);
        String routeWildcardKey = key(method, "*");
        String allWildcardKey = key("*", "*");

        RateLimitPolicy policy = firstPresent(snapshot, routeMethodKey, wildcardMethodKey, routeWildcardKey, allWildcardKey);
        if (policy != null) {
            return policy;
        }

        return new RateLimitPolicy(
            method,
            normalizedRoute,
            properties.defaultUpstreamUrl(),
            properties.defaultCapacity(),
            properties.defaultRefillRatePerSecond(),
            properties.defaultIdentityHeader(),
            properties.defaultFailMode());
    }

    /**
     * Devuelve la antiguedad del snapshot activo en segundos para observabilidad.
     */
    public long snapshotAgeSeconds() {
        long lastRefresh = lastRefreshEpochMillis.get();
        if (lastRefresh <= 0) {
            return 0;
        }

        return Math.max(0, (Instant.now().toEpochMilli() - lastRefresh) / 1000);
    }

    /**
     * Orquesta la carga de reglas y decide fail-fast en bootstrap o fallback en runtime.
     */
    private void refreshInternal(boolean failFastOnStartup) {
        try {
            Map<String, RateLimitPolicy> loaded = loadRules();
            snapshotByKey.set(loaded);
            lastRefreshEpochMillis.set(Instant.now().toEpochMilli());
            LOGGER.info("Loaded {} rate-limit rules from {}", loaded.size(), properties.rulesPath());
        } catch (Exception e) {
            refreshErrors.increment();
            if (failFastOnStartup || snapshotByKey.get() == null) {
                throw new IllegalStateException("Rate limiter startup aborted: invalid rules at " + properties.rulesPath(), e);
            }
            LOGGER.warn("Keeping previous rules snapshot after refresh error: {}", e.getMessage());
        }
    }

    /**
     * Lee y parsea el archivo YAML de reglas, validando que exista al menos una regla.
     */
    private Map<String, RateLimitPolicy> loadRules() throws IOException {
        Path path = Path.of(properties.rulesPath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("Rules file does not exist: " + path.toAbsolutePath());
        }

        RateLimitRulesFile file = yamlMapper.readValue(Files.newInputStream(path), RateLimitRulesFile.class);
        List<RateLimitRule> rules = Optional.ofNullable(file.rules()).orElse(List.of());
        if (rules.isEmpty()) {
            throw new IllegalStateException("Rules file must contain at least one rule");
        }

        return rules.stream()
            .map(this::toPolicy)
            .collect(Collectors.toMap(policy -> key(policy.method(), policy.route()), policy -> policy, (a, b) -> b));
    }

    /**
     * Convierte una regla cruda a politica runtime aplicando defaults y validaciones.
     */
    private RateLimitPolicy toPolicy(RateLimitRule rule) {
        if (rule.method() == null || rule.method().isBlank()) {
            throw new IllegalStateException("Rule method is required");
        }
        if (rule.route() == null || rule.route().isBlank()) {
            throw new IllegalStateException("Rule route is required");
        }

        int capacity = Optional.ofNullable(rule.capacity()).orElse(properties.defaultCapacity());
        int refillRate = Optional.ofNullable(rule.refillRatePerSecond()).orElse(properties.defaultRefillRatePerSecond());
        if (capacity <= 0 || refillRate <= 0) {
            throw new IllegalStateException("Rule capacity and refillRatePerSecond must be > 0");
        }

        String upstreamUrl = Optional.ofNullable(rule.upstreamUrl()).filter(value -> !value.isBlank()).orElse(properties.defaultUpstreamUrl());
        if (upstreamUrl == null || upstreamUrl.isBlank()) {
            throw new IllegalStateException("Rule upstreamUrl or rate-limiter.default-upstream-url is required");
        }

        String identityHeader = Optional.ofNullable(rule.identityHeader())
            .filter(value -> !value.isBlank())
            .orElse(properties.defaultIdentityHeader());
        FailMode failMode = FailMode.from(rule.failMode(), properties.defaultFailMode());

        return new RateLimitPolicy(
            rule.method().toUpperCase(),
            rule.route(),
            upstreamUrl,
            capacity,
            refillRate,
            identityHeader,
            failMode);
    }

    /**
     * Busca la primera politica disponible segun el orden de precedencia recibido.
     */
    private RateLimitPolicy firstPresent(Map<String, RateLimitPolicy> snapshot, String... keys) {
        for (String key : keys) {
            RateLimitPolicy policy = snapshot.get(key);
            if (policy != null) {
                return policy;
            }
        }
        return null;
    }

    /**
     * Genera la clave interna de lookup para metodo y ruta.
     */
    private String key(String method, String route) {
        return method.toUpperCase() + "::" + route;
    }
}

