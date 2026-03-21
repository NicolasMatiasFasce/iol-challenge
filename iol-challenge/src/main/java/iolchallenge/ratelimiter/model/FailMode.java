package iolchallenge.ratelimiter.model;

public enum FailMode {
    FAIL_OPEN,
    FAIL_CLOSED;

    public static FailMode from(String value, FailMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return switch (value.trim().toUpperCase().replace('-', '_')) {
            case "FAIL_OPEN" -> FAIL_OPEN;
            case "FAIL_CLOSED" -> FAIL_CLOSED;
            default -> fallback;
        };
    }
}

