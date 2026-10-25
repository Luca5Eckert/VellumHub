package com.vellumhub.user_service.share.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

/** Header authentication is verified by the internal controller, NOT by the OAuth JWT parser. */
@Configuration(proxyBeanMethods = false)
public class InternalProvisioningSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain internalProvisioningChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/identity/**")
                // A service-key-authenticated, stateless machine endpoint has no browser CSRF token.
                // Do not disable CSRF globally; exempt only the internal provisioning endpoint.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        PathPatternRequestMatcher.pathPattern("/internal/identity/profiles")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/internal/identity/profiles").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
