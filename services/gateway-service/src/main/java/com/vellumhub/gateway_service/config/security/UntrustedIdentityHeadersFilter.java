package com.vellumhub.gateway_service.config.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * An inbound client cannot assert an authenticated principal through proxy headers.
 * The validated JWT remains the authority for downstream identity.
 */
@Component
public final class UntrustedIdentityHeadersFilter implements GlobalFilter, Ordered {

    private static final List<String> UNTRUSTED_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Roles", "X-Roles", "X-Role",
            "X-Authenticated-User", "X-Authenticated-Roles", "X-Auth-User",
            "X-Forwarded-User", "X-Forwarded-Email", "X-Original-User");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> UNTRUSTED_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
