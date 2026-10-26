package com.vellumhub.auth.config;

import com.vellumhub.auth.identity.FederatedIdentityService;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** User-facing OIDC federation; the internal Authorization Server never emits Google subjects. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "vellum.google.enabled", havingValue = "true")
public class GoogleFederationConfig {
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> federatedUsers(FederatedIdentityService identities,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        var delegate = new OidcUserService();
        return request -> {
            try {
                OidcUser googleUser = delegate.loadUser(request);
                var id = identities.resolve(googleUser);
                metrics.counter("vellum.auth.federation", "outcome", "resolved").increment();
                return new CanonicalOidcUser(id.toString(), googleUser);
            } catch (org.springframework.security.oauth2.core.OAuth2AuthenticationException denied) {
                metrics.counter("vellum.auth.federation", "outcome", "rejected").increment();
                throw denied;
            }
        };
    }

    /** Keep the signed upstream ID token intact, override only the local Authentication name. */
    public record CanonicalOidcUser(String canonicalId, OidcUser upstream, java.time.Instant authenticatedAt)
            implements OidcUser, Serializable {
        public CanonicalOidcUser(String canonicalId, OidcUser upstream) {
            this(canonicalId, upstream, java.time.Instant.now());
        }
        @Override public String getName() { return canonicalId; }
        @Override public Map<String, Object> getClaims() { return upstream.getClaims(); }
        @Override public OidcUserInfo getUserInfo() { return upstream.getUserInfo(); }
        @Override public OidcIdToken getIdToken() { return upstream.getIdToken(); }
        @Override public Map<String, Object> getAttributes() { return upstream.getAttributes(); }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            var authorities = new java.util.ArrayList<GrantedAuthority>(upstream.getAuthorities());
            // Spring Security 7's ID-token generator derives auth_time from factor
            // authorities, not from an optional upstream Google auth_time claim.
            authorities.add(org.springframework.security.core.authority.FactorGrantedAuthority
                    .withAuthority(org.springframework.security.core.authority.FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY)
                    .issuedAt(authenticatedAt).build());
            return authorities;
        }
    }
}
