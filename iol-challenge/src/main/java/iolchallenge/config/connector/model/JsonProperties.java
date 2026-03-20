package iolchallenge.config.connector.model;

import org.springframework.lang.Nullable;

import java.util.Optional;

public record JsonProperties(@Nullable JsonFormat format,
                             @Nullable String zonedDateTimePattern,
                             @Nullable String localDateTimePattern,
                             @Nullable String localDatePattern,
                             @Nullable String localTimePattern) {
    public static JsonProperties of(JsonFormat format) {
        return new JsonProperties(format, null, null, null, null);
    }

    public JsonFormat format() {
        return Optional.ofNullable(format).orElse(ConnectorDefaults.JSON_PROPERTIES_FORMAT);
    }

    public boolean hasCustomPatterns() {
        return zonedDateTimePattern != null || localDateTimePattern != null || localDatePattern != null || localTimePattern != null;
    }
}
