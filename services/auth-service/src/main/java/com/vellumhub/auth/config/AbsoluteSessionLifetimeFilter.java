package com.vellumhub.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforce absolute age separately from Spring Session's sliding *idle* expiration. */
public final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {
    private final Duration absoluteTtl;
    private final Clock clock;

    public AbsoluteSessionLifetimeFilter(Duration absoluteTtl, Clock clock) {
        this.absoluteTtl = absoluteTtl;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                long ageMillis = clock.millis() - session.getCreationTime();
                if (ageMillis >= absoluteTtl.toMillis()) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                }
            } catch (IllegalStateException concurrentInvalidation) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
