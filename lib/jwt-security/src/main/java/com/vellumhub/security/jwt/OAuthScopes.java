package com.vellumhub.security.jwt;

import java.util.Set;

/** Explicit client capabilities; roles never imply scopes. */
public final class OAuthScopes {
    private OAuthScopes() { }
    public static final Set<String> API = Set.of(
            "user:read", "user:write", "catalog:read", "catalog:write",
            "catalog:personal:read", "catalog:personal:write",
            "catalog:requests:read", "catalog:requests:write",
            "engagement:read", "engagement:write", "recommendation:read");
    public static final String DEFAULT_CLIENT_SCOPES = "user:read,catalog:read,catalog:personal:read,"
            + "catalog:personal:write,catalog:requests:write,engagement:read,engagement:write,recommendation:read";
}
