package com.vellumhub.gateway_service.config.rate;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimiterConfig {


    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    String userId = jwtAuth.getToken().getClaimAsString("user_id");

                    return "user:" + Objects.requireNonNullElseGet(userId, jwtAuth::getName);
                })
                .switchIfEmpty(Mono.defer(() -> Mono.just("ip:" + extractClientIp(exchange))))
                .onErrorResume(e -> Mono.just("ip:" + extractClientIp(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + extractClientIp(exchange));
    }

    /**
     * Extracts the client's IP address from the request, considering possible proxy headers.
     * @param exchange the current server exchange
     * @return the client's IP address as a string
     */
    private String extractClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

}