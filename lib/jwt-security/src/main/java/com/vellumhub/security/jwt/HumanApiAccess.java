package com.vellumhub.security.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Domain HTTP APIs accept delegated human access only, including human administrators. */
public final class HumanApiAccess {
    private HumanApiAccess() { }

    public static boolean allowed(Authentication authentication, String scope, boolean adminOnly) {
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()
                || !"authorization_code".equals(token.getToken().getClaimAsString("grant_type"))) {
            return false;
        }
        var authorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
        return authorities.contains("SCOPE_" + scope)
                && (adminOnly ? authorities.contains("ROLE_ADMIN")
                    : authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN"));
    }
}
