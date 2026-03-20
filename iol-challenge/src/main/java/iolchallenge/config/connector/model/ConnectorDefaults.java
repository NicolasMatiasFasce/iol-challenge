package iolchallenge.config.connector.model;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

class ConnectorDefaults {

    private ConnectorDefaults() {
        throw new IllegalStateException("Utility class");
    }

    public static final boolean SECURE = false;
    public static final int MAX_CONNECTIONS = 20;
    public static final Duration CONNECTION_TIMEOUT = Duration.ofMillis(500);
    public static final Duration READ_TIMEOUT = Duration.of(30, ChronoUnit.SECONDS);
    public static final Duration IDLE_CONNECTION_TIMEOUT = Duration.of(60, ChronoUnit.SECONDS);
    public static final Duration VALIDATE_AFTER_INACTIVITY = Duration.ofMillis(-1); // off
    public static final boolean CACHE_ENABLED = false;
    public static final int CACHE_SIZE = 1000;
    public static final int CACHE_CONCURRENCY_LEVEL = 50;
    public static final List<String> CACHE_SUPPORTED_METHODS = List.of("MAX_AGE", "EXPIRES", "ETAG", "LAST_MODIFIED");
    public static final CacheProperties CACHE_PROPERTIES = new CacheProperties(CACHE_ENABLED, CACHE_SIZE, CACHE_CONCURRENCY_LEVEL, CACHE_SUPPORTED_METHODS);
    public static final int REQUEST_MAX_RETRIES = 1;
    public static final RequestProperties REQUEST_PROPERTIES = new RequestProperties(ConnectorDefaults.REQUEST_MAX_RETRIES);
    public static final boolean SHUTDOWN_HOOK_ENABLED = false;
    public static final JsonFormat JSON_PROPERTIES_FORMAT = JsonFormat.SNAKE_CASE;
    public static final JsonProperties JSON_PROPERTIES = JsonProperties.of(JSON_PROPERTIES_FORMAT);
}
