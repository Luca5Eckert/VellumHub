package com.vellumhub.gateway_service.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedIdentityHeadersFilterTest {

    @Test
    void stripsSpoofedIdentityButPreservesBearerAuthorization() {
        var request = MockServerHttpRequest.get("/api/v1/catalog/books")
                .header("X-User-Id", "admin")
                .header("x-usER-rOles", "ADMIN")
                .header("X-Forwarded-User", "another-user")
                .header("Authorization", "Bearer test-token")
                .header("X-Request-Id", "trace-test").build();
        var seen = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = exchange -> {
            seen.set(exchange);
            return Mono.empty();
        };
        new UntrustedIdentityHeadersFilter().filter(MockServerWebExchange.from(request), chain).block();

        var headers = seen.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-User-Roles")).isNull();
        assertThat(headers.getFirst("X-Forwarded-User")).isNull();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("trace-test");
    }
}
