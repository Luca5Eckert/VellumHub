package com.vellumhub.gateway_service.config.rate;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterSecurityTest {

    private MockServerWebExchange request(String remoteIp, String forwardedFor) {
        var builder = MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(remoteIp, 5050));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void doesNotTrustClientSuppliedForwardedIp() {
        var limiter = new RateLimiterConfig("");
        assertThat(limiter.ipKeyResolver().resolve(request("192.0.2.10", "198.51.100.55")).block())
                .isEqualTo("ip:192.0.2.10");
    }

    @Test
    void usesLastUntrustedHopBehindExplicitlyTrustedProxy() {
        var limiter = new RateLimiterConfig("10.0.0.0/8,172.16.0.0/12");
        assertThat(limiter.ipKeyResolver().resolve(
                request("10.0.0.4", "203.0.113.99, 192.0.2.15, 172.16.0.2")).block())
                .isEqualTo("ip:192.0.2.15");
    }

    @Test
    void malformedForwardingFallsBackToPeer() {
        var limiter = new RateLimiterConfig("10.0.0.0/8");
        assertThat(limiter.ipKeyResolver().resolve(request("10.0.0.4", "evil.example")).block())
                .isEqualTo("ip:10.0.0.4");
    }

    @Test
    void invalidTrustedProxyConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> new RateLimiterConfig("not-a-cidr"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiterConfig("0.0.0.0/0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesAuthenticatedRateLimitUserKey() {
        var limiter = new RateLimiterConfig("");
        assertThat(limiter.userKeyResolver().resolve(request("192.0.2.10", "198.51.100.55")).block())
                .isEqualTo("ip:192.0.2.10");
    }
}
