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
    OAuth2UserService<OidcUserRequest, OidcUser> federatedUsers(FederatedIdentityService identities) {
        var delegate = new OidcUserService();
        return request -> {
            OidcUser googleUser = delegate.loadUser(request);
            var id = identities.resolve(googleUser);
            return new CanonicalOidcUser(id.toString(), googleUser);
        };
    }

    /** Keep the signed upstream ID token intact, override only the local Authentication name. */
    public record CanonicalOidcUser(String canonicalId, OidcUser upstream)
            implements OidcUser, Serializable {
        @Override public String getName() { return canonicalId; }
        @Override public Map<String, Object> getClaims() { return upstream.getClaims(); }
        @Override public OidcUserInfo getUserInfo() { return upstream.getUserInfo(); }
        @Override public OidcIdToken getIdToken() { return upstream.getIdToken(); }
        @Override public Map<String, Object> getAttributes() { return upstream.getAttributes(); }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            return upstream.getAuthorities();
        }
    }
}
