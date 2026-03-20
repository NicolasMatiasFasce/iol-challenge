package iolchallenge.config.connector;

import iolchallenge.config.connector.model.JsonProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static java.time.format.DateTimeFormatter.ofPattern;

public class MapperFactory {

    private MapperFactory() {
        throw new IllegalStateException("Utility class");
    }

    public static ObjectMapper createMapper(JsonProperties properties) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        registerCustomJavaApiTimeModule(mapper, properties);

        mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        switch (properties.format()) {
            case SNAKE_CASE -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            case CAMEL_CASE -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        }

        return mapper;
    }

    public static void registerCustomJavaApiTimeModule(ObjectMapper mapper, JsonProperties properties) {
        if (properties.hasCustomPatterns()) {
            SimpleModule module = new SimpleModule();

            if (properties.zonedDateTimePattern() != null) {
                var formatter = ofPattern(properties.zonedDateTimePattern());
                module.addDeserializer(ZonedDateTime.class, new JavaApiTimeDeserializer<>(value -> ZonedDateTime.parse(value, formatter)));
                module.addSerializer(ZonedDateTime.class, new JavaApiTimeSerializer<>(value -> value.format(formatter)));
            }

            if (properties.localDateTimePattern() != null) {
                var formatter = ofPattern(properties.localDateTimePattern());
                module.addDeserializer(LocalDateTime.class, new JavaApiTimeDeserializer<>(value -> LocalDateTime.parse(value, formatter)));
                module.addSerializer(LocalDateTime.class, new JavaApiTimeSerializer<>(value -> value.format(formatter)));
            }

            if (properties.localDatePattern() != null) {
                var formatter = ofPattern(properties.localDatePattern());
                module.addDeserializer(LocalDate.class, new JavaApiTimeDeserializer<>(value -> LocalDate.parse(value, formatter)));
                module.addSerializer(LocalDate.class, new JavaApiTimeSerializer<>(value -> value.format(formatter)));
            }

            if (properties.localTimePattern() != null) {
                var formatter = ofPattern(properties.localTimePattern());
                module.addDeserializer(LocalTime.class, new JavaApiTimeDeserializer<>(value -> LocalTime.parse(value, formatter)));
                module.addSerializer(LocalTime.class, new JavaApiTimeSerializer<>(value -> value.format(formatter)));
            }

            mapper.registerModule(module);
        }
    }

    protected static class JavaApiTimeDeserializer<T> extends JsonDeserializer<T> {
        private final Function<String, T> deserialize;

        public JavaApiTimeDeserializer(Function<String, T> deserialize) {
            this.deserialize = deserialize;
        }

        @Override
        public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return deserialize.apply(jp.getValueAsString());
        }
    }

    protected static class JavaApiTimeSerializer<T> extends JsonSerializer<T> {
        private final Function<T, String> serialize;

        public JavaApiTimeSerializer(Function<T, String> serialize) {
            this.serialize = serialize;
        }

        @Override
        public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(serialize.apply(value));
        }
    }
}
