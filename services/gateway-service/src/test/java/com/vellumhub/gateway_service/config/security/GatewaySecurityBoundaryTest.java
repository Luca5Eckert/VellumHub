package com.vellumhub.gateway_service.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "VELLUM_DOCS_ENABLED=false"
})
class GatewaySecurityBoundaryTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void invalidBearerUsesTheJsonAuthenticationContract() {
        client().get().uri("/api/v1/catalog/books")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token")
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON)
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}");
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
    void authenticationFailuresDoNotExposeCanarySecretsInGatewayLogs() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var collector = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        collector.start();
        logger.addAppender(collector);
        try {
            client().get().uri("/api/v1/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer sec01-access-canary-6a74")
                    .header(HttpHeaders.COOKIE, "SESSION=sec01-cookie-canary-7b85")
                    .exchange().expectStatus().isUnauthorized();
        } finally {
            logger.detachAppender(collector);
            collector.stop();
        }
        assertThat(collector.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList())
                .noneMatch(message -> message.contains("sec01-access-canary-6a74")
                        || message.contains("sec01-cookie-canary-7b85"));
    }

    @Test
    void corsUsesOnlyExplicitOriginsAndPreservesExistingCredentialsMode() {
        var config = new GatewaySecurityConfig(
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
