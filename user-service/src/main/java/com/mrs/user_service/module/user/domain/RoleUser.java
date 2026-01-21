package com.mrs.user_service.module.user.domain;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

public enum RoleUser implements GrantedAuthority {
    ADMIN("ADMIN"),
    USER("USER");

    private final String authority;

    RoleUser(String authority) {
        this.authority = authority;
    }

    @Override
    public String getAuthority() {
        return authority;
    }
}
