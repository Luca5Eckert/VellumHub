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
import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;
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
    PasswordEncoder passwordEncoder(
            @Value("${vellum.auth.password.argon2.memory-kib:19456}") int memoryKiB,
            @Value("${vellum.auth.password.argon2.iterations:2}") int iterations,
            @Value("${vellum.auth.password.argon2.parallelism:1}") int parallelism) {
        if (memoryKiB < 19456 || iterations < 2 || parallelism < 1) {
            throw new IllegalArgumentException(
                    "Argon2id parameters must meet the configured security floor");
        }
        var argon2 = Argon2Function.getInstance(memoryKiB, iterations, parallelism, 32, Argon2.ID);
        return new Argon2Password4jPasswordEncoder(argon2);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbc,
            @Value("${vellum.client.enabled:false}") boolean clientEnabled,
            @Value("${vellum.client.id:vellumhub-spa-poc}") String clientId,
            @Value("${vellum.client.redirect-uri:}") String redirectUri,
            @Value("${vellum.client.post-logout-uri:}") String postLogoutUri) {
        var repository = new JdbcRegisteredClientRepository(jdbc);
        if (!clientEnabled) {
            return repository;
        }
        if (redirectUri.isBlank() || postLogoutUri.isBlank()) {
            throw new IllegalStateException("Enabled OAuth client requires redirect and post-logout URIs");
        }
        RegisteredClient existing = repository.findByClientId(clientId);
        if (existing == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientName("VellumHub public OIDC client")
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
        var mapper = authorizationJsonMapper();
        var service = new JdbcOAuth2AuthorizationService(jdbc, clients);
        service.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, mapper));
        service.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(mapper));
        return service;
    }

    static tools.jackson.databind.json.JsonMapper authorizationJsonMapper() {
        return tools.jackson.databind.json.JsonMapper.builder()
                .addModules(org.springframework.security.jackson.SecurityJacksonModules
                        .getModules(AuthorizationPersistenceConfig.class.getClassLoader(),
                                tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                                        .allowIfSubType(GoogleFederationConfig.CanonicalOidcUser.class)))
                .addMixIn(GoogleFederationConfig.CanonicalOidcUser.class, CanonicalUserMixin.class)
                .build();

    }

    // Explicitly allow this one application type; never enable unrestricted default typing.
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS)
    @com.fasterxml.jackson.annotation.JsonAutoDetect(
            fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
            getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class CanonicalUserMixin {
        @com.fasterxml.jackson.annotation.JsonCreator
        CanonicalUserMixin(
                @com.fasterxml.jackson.annotation.JsonProperty("canonicalId") String canonicalId,
                @com.fasterxml.jackson.annotation.JsonProperty("upstream")
                org.springframework.security.oauth2.core.oidc.user.OidcUser upstream,
                @com.fasterxml.jackson.annotation.JsonProperty("authenticatedAt") java.time.Instant authenticatedAt) { }
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
}
