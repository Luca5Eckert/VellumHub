package com.vellumhub.gateway_service.config.rate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Configuration
public class RateLimiterConfig {

    private final List<TrustedNetwork> trustedProxies;

    public RateLimiterConfig(@Value("${VELLUM_TRUSTED_PROXY_CIDRS:}") String trustedProxyCidrs) {
        trustedProxies = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(TrustedNetwork::parse).toList();
    }

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    String userId = jwtAuth.getToken().getClaimAsString("user_id");
                    return "user:" + Objects.requireNonNullElseGet(userId, jwtAuth::getName);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + extractClientIp(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.fromSupplier(() -> "ip:" + extractClientIp(exchange));
    }

    /**
     * Do not trust X-Forwarded-For sent by a direct client. When the TCP peer is a
     * trusted proxy, walk the chain from the right, skipping trusted proxies.
     * A trusted ingress MUST append the observed peer and discard untrusted
     * forwarding headers when necessary. CIDRs are configured per deployment.
     */
    String extractClientIp(ServerWebExchange exchange) {
        var remoteSocket = exchange.getRequest().getRemoteAddress();
        if (remoteSocket == null || remoteSocket.getAddress() == null) {
            return "unknown";
        }
        InetAddress remote = remoteSocket.getAddress();
        String fallback = remote.getHostAddress();
        if (!isTrusted(remote)) {
            return fallback;
        }
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return fallback;
        }
        String[] chain = forwarded.split(",", -1);
        for (int i = chain.length - 1; i >= 0; i--) {
            InetAddress address = parseNumericAddress(chain[i].trim());
            if (address == null) {
                return fallback; // Malformed proxy chain must not create arbitrary quota keys.
            }
            if (!isTrusted(address)) {
                return address.getHostAddress();
            }
        }
        return fallback;
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private static InetAddress parseNumericAddress(String value) {
        if (value.isBlank() || !value.matches("[0-9a-fA-F:.]+")
                || (!value.contains(":") && !value.contains("."))) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private record TrustedNetwork(byte[] network, int prefixLength) {
        static TrustedNetwork parse(String value) {
            String[] pieces = value.split("/", -1);
            if (pieces.length != 2) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value);
            }
            InetAddress address = parseNumericAddress(pieces[0]);
            if (address == null) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR address: " + value);
            }
            try {
                int prefix = Integer.parseInt(pieces[1]);
                if (prefix < 0 || prefix > address.getAddress().length * 8) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + value);
                }
                return new TrustedNetwork(address.getAddress(), prefix);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + value, exception);
            }
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            for (int bit = 0; bit < prefixLength; bit++) {
                int mask = 1 << (7 - (bit % 8));
                int index = bit / 8;
                if ((candidate[index] & mask) != (network[index] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
