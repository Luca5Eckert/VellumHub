package com.vellumhub.gateway_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDocumentationConfigurationTest {

    private static final Map<String, String> EXPECTED_SWAGGER_DEFINITIONS = Map.of(
            "User Service", "/docs/user/v3/api-docs",
            "Catalog Service", "/docs/catalog/v3/api-docs",
            "Engagement Service", "/docs/engagement/v3/api-docs",
            "Recommendation Service", "/docs/recommendation/v3/api-docs"
    );

    private static final Map<String, String> EXPECTED_DOCUMENTATION_ROUTES = Map.of(
            "user-openapi", "Path=/docs/user/v3/api-docs",
            "catalog-openapi", "Path=/docs/catalog/v3/api-docs",
            "engagement-openapi", "Path=/docs/engagement/v3/api-docs",
            "recommendation-openapi", "Path=/docs/recommendation/v3/api-docs"
    );

    @Test
    void shouldKeepEveryServiceAvailableInCentralizedOpenApiConfiguration() throws IOException {
        PropertySourcesPropertyResolver resolver = loadApplicationConfiguration();

        assertThat(resolver.getProperty("springdoc.swagger-ui.path"))
                .isEqualTo("/docs");
        assertThat(resolver.getProperty(
                "springdoc.swagger-ui.disable-swagger-default-url",
                Boolean.class
        )).isTrue();

        assertThat(readSwaggerDefinitions(resolver))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_SWAGGER_DEFINITIONS);
        assertThat(readDocumentationRoutes(resolver))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_DOCUMENTATION_ROUTES);
    }

    private PropertySourcesPropertyResolver loadApplicationConfiguration() throws IOException {
        var loader = new YamlPropertySourceLoader();
        var resource = new ClassPathResource("application.yml");
        var propertySources = new MutablePropertySources();

        loader.load("application", resource).forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }

    private Map<String, String> readSwaggerDefinitions(PropertySourcesPropertyResolver resolver) {
        Map<String, String> definitions = new HashMap<>();

        for (int index = 0; ; index++) {
            String name = resolver.getProperty("springdoc.swagger-ui.urls[" + index + "].name");
            if (name == null) {
                break;
            }

            String url = resolver.getProperty("springdoc.swagger-ui.urls[" + index + "].url");
            definitions.put(name, url);
        }

        return definitions;
    }

    private Map<String, String> readDocumentationRoutes(PropertySourcesPropertyResolver resolver) {
        Map<String, String> routes = new HashMap<>();

        for (int index = 0; ; index++) {
            String prefix = "spring.cloud.gateway.server.webflux.routes[" + index + "]";
            String id = resolver.getProperty(prefix + ".id");
            if (id == null) {
                break;
            }

            if (EXPECTED_DOCUMENTATION_ROUTES.containsKey(id)) {
                routes.put(id, resolver.getProperty(prefix + ".predicates[0]"));
            }
        }

        return routes;
    }
}
