package com.mrs.user_service.module.auth.infrastructure.security.authenticator;

import com.mrs.user_service.module.auth.domain.model.AuthenticatedUser;
import com.mrs.user_service.module.auth.domain.port.AuthenticatorPort;
import com.mrs.user_service.module.auth.infrastructure.security.user.UserDetailImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityAuthenticatorAdapter implements AuthenticatorPort {

    private final AuthenticationManager authenticationManager;

    public SpringSecurityAuthenticatorAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthenticatedUser authenticate(String email, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );


        UserDetailImpl principal = (UserDetailImpl) auth.getPrincipal();

        return new AuthenticatedUser(
                principal.getUserId(),
                principal.getUsername(),
                principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
    }
}