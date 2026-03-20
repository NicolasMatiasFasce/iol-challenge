package iolchallenge.config.connector.model;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public record CacheProperties(@Nullable Boolean enabled,
                              @Nullable Integer size,
                              @Nullable Integer concurrencyLevel,
                              @Nullable List<String> supportedMethods) {
    public Boolean enabled() {
        return Optional.ofNullable(enabled).orElse(ConnectorDefaults.CACHE_ENABLED);
    }

    public Integer size() {
        return Optional.ofNullable(size).orElse(ConnectorDefaults.CACHE_SIZE);
    }

    public Integer concurrencyLevel() {
        return Optional.ofNullable(concurrencyLevel).orElse(ConnectorDefaults.CACHE_CONCURRENCY_LEVEL);
    }

    public List<String> supportedMethods() {
        return Optional.ofNullable(supportedMethods).orElse(ConnectorDefaults.CACHE_SUPPORTED_METHODS);
    }
}
