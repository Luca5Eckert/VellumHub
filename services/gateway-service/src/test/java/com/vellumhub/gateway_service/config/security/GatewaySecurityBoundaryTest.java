package com.vellumhub.gateway_service.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_KEY=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMzItaXRz",
        "VELLUM_DOCS_ENABLED=false"
})
class GatewaySecurityBoundaryTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void protectedServiceWithoutTokenIsUnauthorized() {
        client().get().uri("/api/v1/catalog/books").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unlistedAuthMethodIsNotPublic() {
        client().get().uri("/api/v1/auth/login").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/v1/auth/unknown").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void documentationIsNotPublicByDefault() {
        client().get().uri("/docs").exchange().expectStatus().is4xxClientError();
        client().get().uri("/docs/user/v3/api-docs").exchange().expectStatus().is4xxClientError();
    }

    @Test
    void corsUsesOnlyExplicitOriginsAndPreservesExistingCredentialsMode() {
        var config = new GatewaySecurityConfig(
                "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMzItaXRz",
                false, "https://frontend.example");
        var request = org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .options("/api/v1/auth/login")
                .header("Origin", "https://frontend.example")
                .header("Access-Control-Request-Method", "POST").build();
        var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(request);
        var cors = config.corsSource().getCorsConfiguration(exchange);
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://frontend.example");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedOrigins()).doesNotContain("*");
    }

    @Test
    void corsDoesNotGrantAuthenticationToAnOrigin() {
        client().get().uri("/api/v1/catalog/books")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .exchange().expectStatus().isForbidden();
    }
}
