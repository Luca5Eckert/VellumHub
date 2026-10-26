package com.vellumhub.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/** Shared fixed-window limit for credential proofs and OIDC starts. No raw IP or account label. */
final class AuthenticationThrottleFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final int limit;
    private final Clock clock;

    AuthenticationThrottleFilter(JdbcTemplate jdbc, MeterRegistry metrics, int limit, Clock clock) {
        if (limit < 1) { throw new IllegalArgumentException("Authentication limit must be positive"); }
        this.jdbc = jdbc;
        this.metrics = metrics;
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        boolean proof = "POST".equals(request.getMethod()) && ("/login".equals(path)
                || "/v1/identity/google/link/challenge".equals(path)
                || "/v1/identity/google/link".equals(path));
        boolean oidc = "GET".equals(request.getMethod()) && "/oauth2/authorization/google".equals(path);
        if (proof || oidc) {
            long window = clock.millis() / 60000 * 60000;
            jdbc.update("DELETE FROM auth_attempt_window WHERE window_start < ?", window - 120000);
            Integer attempts = jdbc.queryForObject("""
                    INSERT INTO auth_attempt_window (remote_hash, window_start, attempts) VALUES (?, ?, 1)
                    ON CONFLICT (remote_hash, window_start)
                    DO UPDATE SET attempts = LEAST(auth_attempt_window.attempts + 1, 1000000)
                    RETURNING attempts
                    """, Integer.class, hash(request.getRemoteAddr()), window);
            if (attempts == null || attempts > limit) {
                metrics.counter("vellum.auth.attempts", "outcome", "throttled").increment();
                response.setHeader("Retry-After", Long.toString(60 - clock.millis() / 1000 % 60));
                response.sendError(429, "Too many authentication attempts");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
