package iolchallenge.config.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import iolchallenge.config.connector.model.ClientResourceProperties;
import iolchallenge.config.connector.model.ConnectorProperties;
import iolchallenge.config.connector.model.RestConnectorProperties;
import iolchallenge.exception.ConnectorNotInitialized;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static iolchallenge.config.connector.MapperFactory.createMapper;
import static java.util.Optional.ofNullable;

@Component
public class ConnectorFactory {
    public Connector create(
        String name,
        ConnectorProperties connectorProperties,
        List<java.util.function.Consumer<java.net.http.HttpRequest.Builder>> customInterceptors)
    {
        Map<String, ClientResourceProperties> resourcesByName = ofNullable(connectorProperties.resources())
            .orElse(Map.of())
            .values()
            .stream()
            .collect(Collectors.toMap(ClientResourceProperties::name, Function.identity(), (first, second) -> second));

        RestConnectorProperties properties = connectorProperties.restConnector();
        HttpClient httpClient = createHttpClient(name, properties);
        ObjectMapper mapper = createMapper(properties.json());
        URI baseUri = createBaseUri(properties);
        Map<String, String> defaultHeaders = createDefaultHeaders(properties);

        return new Connector(
            name,
            httpClient,
            baseUri,
            mapper,
            resourcesByName,
            defaultHeaders,
            properties.request().maxRetries(),
            properties.readTimeout(),
            customInterceptors);
    }

    public Connector create(String name, ConnectorProperties connectorProperties) {
        return create(name, connectorProperties, Collections.emptyList());
    }

    private HttpClient createHttpClient(String name, RestConnectorProperties properties) {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);

            SSLContext sslContext = createSSLContext(name, properties);
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }

            return builder.build();
        } catch (Exception e) {
            throw new ConnectorNotInitialized(name, e);
        }
    }

    private URI createBaseUri(RestConnectorProperties properties) {
        String scheme = ofNullable(properties.protocol()).orElse(properties.secure() ? "https" : "http");
        String endpoint = normalizeEndpoint(properties.endpoint());
        return URI.create("%s://%s%s".formatted(scheme, properties.host(), endpoint));
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }

        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private Map<String, String> createDefaultHeaders(RestConnectorProperties properties) {
        Map<String, String> headers = new java.util.HashMap<>();
        ofNullable(properties.clientId()).ifPresent(value -> headers.put("X-Client", value));
        ofNullable(properties.xVersion()).ifPresent(value -> headers.put("X-Version", value));
        return Map.copyOf(headers);
    }

    private SSLContext createSSLContext(String name, RestConnectorProperties properties) {
        return properties.secure() ? createSSLContext(name) : null;
    }

    private SSLContext createSSLContext(String name) {
        try {
            var context = SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new ConnectorNotInitialized(name, e);
        }
    }
}
