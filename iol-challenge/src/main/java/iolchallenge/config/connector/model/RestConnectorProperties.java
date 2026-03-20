package iolchallenge.config.connector.model;

import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Optional;

public record RestConnectorProperties(String host,
                                      String protocol,
                                      String endpoint,
                                      @Nullable Boolean secure,
                                      @Nullable Boolean shutdownHookEnabled,
                                      @Nullable Integer maxConnections,
                                      @Nullable Duration connectionTimeout,
                                      @Nullable Duration readTimeout,
                                      @Nullable Duration idleConnectionTimeout,
                                      @Nullable CacheProperties cache,
                                      @Nullable JsonProperties json,
                                      @Nullable Duration validateAfterInactivity,
                                      @Nullable RequestProperties request,
                                      @Nullable String xVersion,
                                      @Nullable String clientId) {
    public Boolean secure() {
        return Optional.ofNullable(secure).orElse(ConnectorDefaults.SECURE);
    }

    public Integer maxConnections() {
        return Optional.ofNullable(maxConnections).orElse(ConnectorDefaults.MAX_CONNECTIONS);
    }

    public Duration connectionTimeout() {
        return Optional.ofNullable(connectionTimeout).orElse(ConnectorDefaults.CONNECTION_TIMEOUT);
    }

    public Duration readTimeout() {
        return Optional.ofNullable(readTimeout).orElse(ConnectorDefaults.READ_TIMEOUT);
    }

    public Duration idleConnectionTimeout() {
        return Optional.ofNullable(idleConnectionTimeout).orElse(ConnectorDefaults.IDLE_CONNECTION_TIMEOUT);
    }

    public Duration validateAfterInactivity() {
        return Optional.ofNullable(validateAfterInactivity).orElse(ConnectorDefaults.VALIDATE_AFTER_INACTIVITY);
    }

    public CacheProperties cache() {
        return Optional.ofNullable(cache).orElse(ConnectorDefaults.CACHE_PROPERTIES);
    }

    public RequestProperties request() {
        return Optional.ofNullable(request).orElse(ConnectorDefaults.REQUEST_PROPERTIES);
    }

    public Boolean shutdownHookEnabled() {
        return Optional.ofNullable(shutdownHookEnabled).orElse(ConnectorDefaults.SHUTDOWN_HOOK_ENABLED);
    }

    public JsonProperties json() {
        return Optional.ofNullable(json).orElse(ConnectorDefaults.JSON_PROPERTIES);
    }
}
