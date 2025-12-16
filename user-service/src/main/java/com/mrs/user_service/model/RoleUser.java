package com.mrs.user_service.model;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

public enum RoleUser implements GrantedAuthority {
    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER");

    private final String authority;

    RoleUser(String authority) {
        this.authority = authority;
    }


    @Override
    public @Nullable String getAuthority() {
        return authority;
    }
}
