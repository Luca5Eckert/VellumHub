package com.vellumhub.auth.identity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * The login is an email; the authenticated principal name is the IMMUTABLE user ID.
 * Spring Authorization Server therefore emits UUID subjects, never email subjects.
 */
@Service
public class IdentityDetailsService implements UserDetailsService {
    private final IdentityRepository identities;

    public IdentityDetailsService(IdentityRepository identities) {
        this.identities = identities;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return identities.findByLogin(login)
                .map(identity -> User.withUsername(identity.id().toString())
                        .password(identity.passwordHash())
                        .authorities("ROLE_USER")
                        .disabled(!identity.active())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
