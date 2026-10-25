package com.vellumhub.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Pins signing algorithm, Google JWKS and issuer; never follows a token-provided JWKS URL. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "vellum.google.enabled", havingValue = "true")
public class GoogleLinkTokenConfig {
    @Bean("googleIdTokenDecoder")
    JwtDecoder googleIdTokenDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://accounts.google.com"));
        return decoder;
    }
}
