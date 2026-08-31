package com.vellumhub.gateway_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDocumentationConfigurationTest {

    private static final Set<String> EXPECTED_ROUTE_IDS = Set.of(
            "user-openapi",
            "catalog-openapi",
            "engagement-openapi",
            "recommendation-openapi"
    );

    private static final Set<String> EXPECTED_ROUTE_PATHS = Set.of(
            "Path=/docs/user/v3/api-docs",
            "Path=/docs/catalog/v3/api-docs",
            "Path=/docs/engagement/v3/api-docs",
            "Path=/docs/recommendation/v3/api-docs"
    );

    private static final Set<String> EXPECTED_SWAGGER_NAMES = Set.of(
            "User Service",
            "Catalog Service",
            "Engagement Service",
            "Recommendation Service"
    );

    private static final Set<String> EXPECTED_SWAGGER_URLS = Set.of(
            "/docs/user/v3/api-docs",
            "/docs/catalog/v3/api-docs",
            "/docs/engagement/v3/api-docs",
            "/docs/recommendation/v3/api-docs"
    );

    @Test
    void shouldKeepEveryServiceAvailableInCentralizedOpenApiConfiguration() throws IOException {
        Set<String> configuredValues = loadApplicationConfigurationValues();

        assertThat(configuredValues)
                .contains("/docs", "true")
                .containsAll(EXPECTED_ROUTE_IDS)
                .containsAll(EXPECTED_ROUTE_PATHS)
                .containsAll(EXPECTED_SWAGGER_NAMES)
                .containsAll(EXPECTED_SWAGGER_URLS);
    }

    @SuppressWarnings("unchecked")
    private Set<String> loadApplicationConfigurationValues() throws IOException {
        var loader = new YamlPropertySourceLoader();
        var resource = new ClassPathResource("application.yml");

        return loader.load("application", resource).stream()
                .map(PropertySource::getSource)
                .filter(Map.class::isInstance)
                .map(source -> (Map<String, Object>) source)
                .flatMap(source -> source.values().stream())
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }
}
