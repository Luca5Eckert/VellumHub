package com.vellumhub.auth.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import com.vellumhub.auth.identity.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/** Durable OAuth state; no InMemory registries and no client secrets for a public SPA. */
@Configuration(proxyBeanMethods = false)
public class AuthorizationPersistenceConfig {
    @Bean
    UuidV7 uuidV7() {
        return new UuidV7(new SecureRandom(), Clock.systemUTC());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbc,
            @Value("${vellum.poc.client-id:vellumhub-spa-poc}") String clientId,
            @Value("${vellum.poc.redirect-uri}") String redirectUri,
            @Value("${vellum.poc.post-logout-uri}") String postLogoutUri) {
        var repository = new JdbcRegisteredClientRepository(jdbc);
        RegisteredClient existing = repository.findByClientId(clientId);
        if (existing == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientName("VellumHub SPA PoC")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirectUri)
                    .postLogoutRedirectUri(postLogoutUri)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(10))
                            .build())
                    .build();
            repository.save(client);
        } else if (!existing.getRedirectUris().equals(java.util.Set.of(redirectUri))
                || !existing.getPostLogoutRedirectUris().equals(java.util.Set.of(postLogoutUri))
                || !existing.getClientAuthenticationMethods().equals(java.util.Set.of(ClientAuthenticationMethod.NONE))
                || !existing.getAuthorizationGrantTypes().equals(java.util.Set.of(AuthorizationGrantType.AUTHORIZATION_CODE))
                || !existing.getClientSettings().isRequireProofKey()) {
            throw new IllegalStateException("Existing SPA registration differs from the required public PKCE policy");
        }
        return repository;
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
}
