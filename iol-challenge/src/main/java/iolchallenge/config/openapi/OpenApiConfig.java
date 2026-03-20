package iolchallenge.config.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(@Value("${app.version}") String version, ApiDocProperties apiDocProperties) {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title(apiDocProperties.title())
                        .description(apiDocProperties.description())
                        .version(version)
                        .contact(new Contact()
                                .name(apiDocProperties.contact().name())
                                .email(apiDocProperties.contact().email())
                                .url(apiDocProperties.contact().url()))
                );
    }

    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            if (openApi.getServers() != null && openApi.getServers().size() == 1) {
                Server server = openApi.getServers().getFirst();
                //Spring expone los servicios como http, pero nebula los expone como https,
                // eso confunde a la detección automática de endpoints de swagger.
                // Para que swagger ui funcione en ese caso, cambiamos el esquema a https
                String url = server.getUrl();
                if (url != null && !url.contains("localhost") && url.startsWith("http://")) {
                    server.setUrl(url.replace("http://", "https://"));
                }
            }
        };
    }

    @Bean
    public ModelResolver modelResolverWithSpringObjectMapper(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @ConfigurationProperties(prefix = "app.api-doc")
    public record ApiDocProperties(String title, String description, ApiDocContact contact) {
    }

    public record ApiDocContact(String name, String email, String url) {
    }
}
