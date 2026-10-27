package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class FederatedAuthorizationSerializationTest {
    @Test void restoresCanonicalPrincipalWithIntactUpstreamSignedToken() {
        String id = UUID.randomUUID().toString();
        var upstream = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")),
                new OidcIdToken("signed-upstream-token", Instant.now(), Instant.now().plusSeconds(300),
                        Map.of("iss", "https://accounts.google.com", "sub", "google-subject")));
        var user = new GoogleFederationConfig.CanonicalOidcUser(id, upstream);
        var principal = new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
        var mapper = AuthorizationPersistenceConfig.authorizationJsonMapper();
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>(Map.of("java.security.Principal", principal)));
        var restored = mapper.readValue(json, Map.class);
        var authentication = (Authentication) restored.get("java.security.Principal");
        assertThat(authentication.getName()).isEqualTo(id);
        var canonical = (GoogleFederationConfig.CanonicalOidcUser) authentication.getPrincipal();
        assertThat(canonical.authenticatedAt()).isEqualTo(user.authenticatedAt());
        var factor = authentication.getAuthorities().stream()
                .filter(org.springframework.security.core.authority.FactorGrantedAuthority.class::isInstance)
                .map(org.springframework.security.core.authority.FactorGrantedAuthority.class::cast).findFirst().orElseThrow();
        assertThat(factor.getIssuedAt()).isEqualTo(user.authenticatedAt());
        assertThat(canonical.getSubject()).isEqualTo("google-subject");
        assertThat(canonical.getIdToken().getTokenValue()).isEqualTo("signed-upstream-token");
    }

    @Test void rejectsUnregisteredApplicationTypes() {
        var mapper = AuthorizationPersistenceConfig.authorizationJsonMapper();
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>(Map.of("untrusted", new Unregistered("value"))));
        assertThatThrownBy(() -> mapper.readValue(json, Map.class))
                .isInstanceOf(tools.jackson.databind.exc.InvalidTypeIdException.class);
    }
    record Unregistered(String value) { }
}
