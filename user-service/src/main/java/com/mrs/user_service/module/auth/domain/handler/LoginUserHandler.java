package com.mrs.user_service.module.auth.domain.handler;

import com.mrs.user_service.share.security.token.TokenProvider;
import com.mrs.user_service.share.security.user.UserDetailImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class LoginUserHandler {

    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;

    public LoginUserHandler(AuthenticationManager authenticationManager, TokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    public String execute(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        UserDetailImpl userDetails = (UserDetailImpl) authentication.getPrincipal();

        return tokenProvider.createToken(
                userDetails.getUsername(),
                userDetails.getUserId(),
                userDetails.getAuthorities()
        );
    }

}
