package com.vellumhub.auth.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/** The Authorization Server's OIDC Logout must resolve sessions across instances, not in memory. */
@Configuration(proxyBeanMethods = false)
public class SessionLifecycleConfig {
    @Bean
    SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessions) {
        return new SpringSessionBackedSessionRegistry<>(sessions);
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<AbsoluteSessionLifetimeFilter> absoluteSessionLifetimeFilter(
            @Value("${vellum.session.absolute-seconds:43200}") long maxAgeSeconds) {
        if (maxAgeSeconds < 60) {
            throw new IllegalArgumentException("Absolute session TTL must be at least 60 seconds");
        }
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                new AbsoluteSessionLifetimeFilter(Duration.ofSeconds(maxAgeSeconds), Clock.systemUTC()));
        // Explicit servlet ordering: Spring Session wraps the request first, then enforce
        // absolute age before Spring Security can restore a context from the session.
        registration.setOrder(org.springframework.session.web.http.SessionRepositoryFilter.DEFAULT_ORDER + 1);
        return registration;
    }
}
