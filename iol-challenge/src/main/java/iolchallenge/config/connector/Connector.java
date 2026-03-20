package iolchallenge.config.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iolchallenge.config.connector.model.ClientResourceProperties;
import iolchallenge.config.connector.model.ContentEncoding;
import iolchallenge.config.connector.model.HttpMethod;
import iolchallenge.exception.ConnectorException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public record Connector(
    String name,
    HttpClient httpClient,
    URI baseUri,
    ObjectMapper objectMapper,
    Map<String, ClientResourceProperties> resourcesByName,
    Map<String, String> defaultHeaders,
    int maxRetries,
    Duration readTimeout,
    List<Consumer<HttpRequest.Builder>> customInterceptors)
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    public RequestBuilder get(String path) {
        return createRequestBuilder(HttpMethod.GET, path);
    }

    public RequestBuilder post(String path) {
        return createRequestBuilder(HttpMethod.POST, path);
    }

    public RequestBuilder put(String path) {
        return createRequestBuilder(HttpMethod.PUT, path);
    }

    public RequestBuilder patch(String path) {
        return createRequestBuilder(HttpMethod.PATCH, path);
    }

    public RequestBuilder delete(String path) {
        return createRequestBuilder(HttpMethod.DELETE, path);
    }

    public RequestBuilder ofResource(String resourceName) {
        ClientResourceProperties resource = ofNullable(resourcesByName.get(resourceName))
                .orElseThrow(() -> new ConnectorException("Connector resource " + resourceName + " is empty"));

        RequestBuilder builder = new RequestBuilder(
            name,
            httpClient,
            baseUri,
            objectMapper,
            resource.method(),
            resource.path(),
            defaultHeaders,
            maxRetries,
            readTimeout,
            customInterceptors);

        ofNullable(resource.mediaType()).ifPresent(builder::mediaType);
        ofNullable(resource.encoding()).ifPresent(builder::encoding);
        ofNullable(resource.headers()).ifPresent(builder::headers);
        ofNullable(resource.params()).ifPresent(builder::params);
        ofNullable(resource.responseTimeout()).ifPresent(builder::responseTimeout);

        return builder;
    }

    private RequestBuilder createRequestBuilder(HttpMethod method, String path) {
        return new RequestBuilder(
            name,
            httpClient,
            baseUri,
            objectMapper,
            method,
            path,
            defaultHeaders,
            maxRetries,
            readTimeout,
            customInterceptors);
    }

    public static class RequestBuilder {
        private final String name;
        private final HttpClient httpClient;
        private final URI baseUri;
        private final ObjectMapper objectMapper;
        private final HttpMethod method;
        private final String path;
        private final Map<String, String> defaultHeaders;
        private final int maxRetries;
        private final Duration defaultReadTimeout;
        private final List<Consumer<HttpRequest.Builder>> customInterceptors;

        private ContentEncoding encoding;
        private String mediaType;
        private Duration responseTimeout;
        private Map<String, String> headers;
        private Map<String, String> params;
        private Map<String, String> pathVariables;
        private Object body;

        public RequestBuilder(
            String name,
            HttpClient httpClient,
            URI baseUri,
            ObjectMapper objectMapper,
            HttpMethod method,
            String path,
            Map<String, String> defaultHeaders,
            int maxRetries,
            Duration defaultReadTimeout,
            List<Consumer<HttpRequest.Builder>> customInterceptors)
        {
            this.name = name;
            this.httpClient = httpClient;
            this.baseUri = baseUri;
            this.objectMapper = objectMapper;
            this.method = method;
            this.path = path;
            this.defaultHeaders = defaultHeaders;
            this.maxRetries = maxRetries;
            this.defaultReadTimeout = defaultReadTimeout;
            this.customInterceptors = customInterceptors;
        }

        public RequestBuilder encoding(ContentEncoding encoding) {
            this.encoding = encoding;
            return this;
        }

        public RequestBuilder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public RequestBuilder param(String key, String value) {
            if (this.params == null) {
                this.params = new HashMap<>();
            }
            this.params.put(key, value);
            return this;
        }

        public RequestBuilder params(Map<String, String> params) {
            if (this.params == null) {
                this.params = new HashMap<>();
            }
            this.params.putAll(params);
            return this;
        }

        public RequestBuilder pathVariable(String key, String value) {
            if (this.pathVariables == null) {
                this.pathVariables = new HashMap<>();
            }
            this.pathVariables.put(key, value);
            return this;
        }

        public RequestBuilder pathVariables(Map<String, String> pathVariables) {
            if (this.pathVariables == null) {
                this.pathVariables = new HashMap<>();
            }
            this.pathVariables.putAll(pathVariables);
            return this;
        }

        public RequestBuilder responseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        public RequestBuilder header(String key, String value) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(key, value);
            return this;
        }

        public RequestBuilder headers(Map<String, String> headers) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.putAll(headers);
            return this;
        }

        public RequestBuilder body(Object body) {
            this.body = body;
            return this;
        }

        public <T> T execute(Class<T> responseType) {
            return execute(responseType, 200);
        }

        public <T> T execute(TypeReference<T> responseType) {
            return execute(responseType, 200);
        }

        public <T> T execute(Class<T> responseType, int... expectedStatus) {
            TypeReference<T> typeReference = new TypeReference<>() {};
            return execute(typeReference, expectedStatus);
        }

        public <T> T execute(TypeReference<T> responseType, int... expectedStatus) {
            HttpResponse<String> response = execute();
            int[] statuses = expectedStatus == null || expectedStatus.length == 0 ? new int[] {200} : expectedStatus;

            if (Arrays.stream(statuses).anyMatch(status -> status == response.statusCode())) {
                String bodyValue = response.body();

                if (responseType.getType().equals(Void.class)) {
                    return null;
                }

                if (responseType.getType().equals(String.class)) {
                    @SuppressWarnings("unchecked")
                    T value = (T) bodyValue;
                    return value;
                }

                if (StringUtils.isBlank(bodyValue)) {
                    return null;
                }

                try {
                    return objectMapper.readValue(bodyValue, responseType);
                } catch (IOException e) {
                    throw new ConnectorException("Error deserializing connector response for " + name, e);
                }
            }

            throw new ConnectorException("Status " + response.statusCode() + ". " + response.body());
        }

        public HttpResponse<String> execute() {
            HttpRequest request = buildHttpRequest();
            try {
                LOGGER.info("Executing {} {}", method, request.uri());
                HttpResponse<String> response = sendWithRetries(request);
                LOGGER.info("{} {} executed! response status: {}", method, request.uri(), response.statusCode());
                return response;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectorException("Error on " + method + " " + request.uri(), e);
            } catch (IOException e) {
                throw new ConnectorException("Error on " + method + " " + request.uri(), e);
            }
        }

        private HttpRequest buildHttpRequest() {
            URI uri = buildURI(
                path,
                Optional.ofNullable(pathVariables).orElse(Collections.emptyMap()),
                Optional.ofNullable(params).orElse(Collections.emptyMap()));
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);

            Duration effectiveTimeout = responseTimeout != null ? responseTimeout : defaultReadTimeout;
            if (effectiveTimeout != null) {
                builder.timeout(effectiveTimeout);
            }

            Map<String, String> effectiveHeaders = new LinkedHashMap<>();
            if (defaultHeaders != null) {
                effectiveHeaders.putAll(defaultHeaders);
            }
            if (headers != null) {
                effectiveHeaders.putAll(headers);
            }
            effectiveHeaders.forEach(builder::header);

            if (mediaType != null) {
                builder.header("Accept", mediaType);
                builder.header("Content-Type", mediaType);
            }

            if (encoding != null) {
                builder.header("Accept-Encoding", encoding.headerValue());
            }

            customInterceptors.forEach(interceptor -> interceptor.accept(builder));

            HttpRequest.BodyPublisher publisher = buildBodyPublisher();
            builder.method(method.name(), publisher);
            return builder.build();
        }

        private HttpRequest.BodyPublisher buildBodyPublisher() {
            if (body == null) {
                return HttpRequest.BodyPublishers.noBody();
            }

            byte[] bytes = serializeBody(body);
            if (encoding != null) {
                bytes = encodeBody(bytes, encoding);
            }

            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        }

        private byte[] serializeBody(Object bodyValue) {
            if (bodyValue instanceof String bodyAsText) {
                return bodyAsText.getBytes(StandardCharsets.UTF_8);
            }

            try {
                return objectMapper.writeValueAsBytes(bodyValue);
            } catch (IOException e) {
                throw new ConnectorException("Error serializing request body for " + name, e);
            }
        }

        private byte[] encodeBody(byte[] bodyBytes, ContentEncoding contentEncoding) {
            try {
                return switch (contentEncoding) {
                    case GZIP -> gzip(bodyBytes);
                    case DEFLATE -> deflate(bodyBytes);
                    case SNAPPY -> Snappy.compress(bodyBytes);
                };
            } catch (IOException e) {
                throw new ConnectorException("Error encoding request body for " + name, e);
            }
        }

        private byte[] gzip(byte[] data) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzipOutputStream = new java.util.zip.GZIPOutputStream(baos)) {
                gzipOutputStream.write(data);
            }
            return baos.toByteArray();
        }

        private byte[] deflate(byte[] data) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream deflaterOutputStream = new java.util.zip.DeflaterOutputStream(baos)) {
                deflaterOutputStream.write(data);
            }
            return baos.toByteArray();
        }

        private HttpResponse<String> sendWithRetries(HttpRequest request) throws IOException, InterruptedException {
            int retries = Math.max(0, maxRetries);
            IOException lastIOException = null;

            for (int attempt = 0; attempt <= retries; attempt++) {
                try {
                    return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    lastIOException = e;
                    if (attempt == retries) {
                        throw e;
                    }
                }
            }

            throw new ConnectorException("Request failed after retries", lastIOException);
        }

        private URI buildURI(String path, Map<String, String> pathVariables, Map<String, String> params) {
            String resolvedPath = replacePathVariables(path, pathVariables);
            String normalizedPath = resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath;
            URI resolvedUri = baseUri.resolve(normalizedPath + queryString(params));
            try {
                return new URI(
                    resolvedUri.getScheme(),
                    resolvedUri.getAuthority(),
                    resolvedUri.getPath(),
                    resolvedUri.getQuery(),
                    resolvedUri.getFragment());
            } catch (URISyntaxException e) {
                throw new ConnectorException("Invalid request URI for " + name + ": " + resolvedUri, e);
            }
        }

        private String replacePathVariables(String path, Map<String, String> pathVariables) {
            int currentPosition = 0;
            StringBuilder builder = new StringBuilder();

            while (currentPosition < path.length()) {
                // Let's find the next opening brace
                int openingBracePosition = path.indexOf("{", currentPosition);

                if (openingBracePosition < 0) {
                    // There is no more opening brace, we can append the rest of the path
                    builder.append(path.substring(currentPosition));
                    // We then update the current position to the end of the path as to exit the loop
                    currentPosition = path.length();
                } else {
                    // We found an opening brace, we append the part of the path before it
                    builder.append(path, currentPosition, openingBracePosition);
                    // We then find the next closing brace
                    int closingBracePosition = path.indexOf("}", openingBracePosition);

                    if (closingBracePosition < 0) {
                        // No closing brace found, we throw an error
                        throw new RuntimeException(
                            "Unexpected end of path: Closing curly brace is missing on path '" + path + "'");
                    }
                    // We found a closing brace, we can extract the variable name
                    String variableName = path.substring(openingBracePosition + 1, closingBracePosition);

                    if (StringUtils.isBlank(variableName)){
                        // If the variable name is empty, we throw an error
                        throw new RuntimeException("Path Variable name is empty on path '" + path + "'");
                    }
                    // If the variable name is not empty, we can look for its value at the map of path variables
                    String valueForReplacement = pathVariables.get(variableName);

                    if (isNull(valueForReplacement)) {
                        // There is no value for the variable, we throw an error
                        throw new RuntimeException(
                            "Path Variable value is not given for '" + variableName + "'");
                    }
                    // If we found a value for the variable, we append it to the buffer instead of the variable name
                    builder.append(URLEncoder.encode(valueForReplacement, StandardCharsets.UTF_8));
                    // We then move to the position after the closing brace
                    currentPosition = closingBracePosition + 1;
                }
            }
            return builder.toString();
        }

        private String queryString(Map<String, String> queryArgs) {
            String query = queryArgs.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isEmpty())
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(joining("&"));

            return query.isEmpty() ? StringUtils.EMPTY : "?" + query;
        }
    }
}
