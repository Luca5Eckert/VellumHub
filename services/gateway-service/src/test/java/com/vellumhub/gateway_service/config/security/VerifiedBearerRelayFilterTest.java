package com.vellumhub.gateway_service.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class VerifiedBearerRelayFilterTest {
    private final VerifiedBearerRelayFilter subject = new VerifiedBearerRelayFilter();

    @Test
    void replacesUntrustedIncomingBearerWithOnlyJwtValidatedBySecurity() {
        var actual = new AtomicReference<String>();
        var request = MockServerHttpRequest.get("/api/v1/catalog/books")
                .header(HttpHeaders.AUTHORIZATION, "Bearer caller-spoof").build();
        var exchange = MockServerWebExchange.from(request);
        var jwt = Jwt.withTokenValue("verified-by-spring-security")
                .header("alg", "RS256")
                .subject("019a0000-1111-7000-8000-000000000001")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build();
        var authenticated = exchange.mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        subject.filter(authenticated, current -> {
            actual.set(current.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();
        assertThat(actual.get()).isEqualTo("Bearer verified-by-spring-security");
    }

    @Test
    void dropsUnvalidatedBearerEvenWhenOtherAuthenticationExists() {
        var actual = new AtomicReference<String>();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/catalog/books")
                .header(HttpHeaders.AUTHORIZATION, "Bearer attacker").build())
                .mutate().principal(Mono.just(new UsernamePasswordAuthenticationToken("any", "x"))).build();
        subject.filter(exchange, current -> {
            actual.set(current.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();
        assertThat(actual.get()).isNull();
    }

    @Test
    void neverRelaysABearerToPublicLegacyLoginRoutes() {
        var actual = new AtomicReference<String>();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer attacker").build());
        subject.filter(exchange, current -> {
            actual.set(current.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        }).block();
        assertThat(actual.get()).isNull();
    }
}
