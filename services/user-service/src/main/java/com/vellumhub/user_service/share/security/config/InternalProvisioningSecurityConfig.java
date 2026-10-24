package com.vellumhub.user_service.share.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Header authentication is verified by the internal controller, NOT by the OAuth JWT parser. */
@Configuration(proxyBeanMethods = false)
public class InternalProvisioningSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain internalProvisioningChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/identity/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/internal/identity/profiles").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
