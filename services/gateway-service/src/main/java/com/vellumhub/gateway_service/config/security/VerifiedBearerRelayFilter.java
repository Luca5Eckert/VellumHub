package com.vellumhub.gateway_service.config.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * SEC-09: Never accept a browser-supplied identity header or relay an
 * unverified bearer. Reactive Spring Security has already authenticated
 * the request before Gateway GlobalFilters execute.
 */
@Component
public final class VerifiedBearerRelayFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().defaultIfEmpty(new java.security.Principal() {
            @Override public String getName() { return "anonymous"; }
        }).flatMap(principal -> {
            var request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(HttpHeaders.AUTHORIZATION);
                        if (principal instanceof JwtAuthenticationToken token) {
                            headers.setBearerAuth(token.getToken().getTokenValue());
                        }
                    }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
