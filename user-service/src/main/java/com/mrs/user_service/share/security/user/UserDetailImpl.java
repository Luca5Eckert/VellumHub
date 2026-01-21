package com.mrs.user_service.share.security.user;

import com.mrs.user_service.module.user.domain.RoleUser;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserDetailImpl implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final RoleUser role;

    public UserDetailImpl(UUID userId, String email, String password, RoleUser role) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(role);
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public UUID getUserId(){
        return userId;
    }
}
