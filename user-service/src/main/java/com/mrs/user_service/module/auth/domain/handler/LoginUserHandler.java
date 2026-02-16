package com.mrs.user_service.module.auth.domain.handler;

import com.mrs.user_service.module.auth.domain.model.AuthenticatedUser;
import com.mrs.user_service.module.auth.domain.port.AuthenticatorPort;
import com.mrs.user_service.module.auth.domain.port.TokenProvider;
import org.springframework.stereotype.Component;

@Component
public class LoginUserHandler {

    private final AuthenticatorPort authenticatorPort;
    private final TokenProvider tokenProvider;

    public LoginUserHandler(AuthenticatorPort authenticatorPort, TokenProvider tokenProvider) {
        this.authenticatorPort = authenticatorPort;
        this.tokenProvider = tokenProvider;
    }

    public String execute(String email, String password) {
        AuthenticatedUser user = authenticatorPort.authenticate(
                email,
                password
        );

        return tokenProvider.createToken(
                user.email(),
                user.id(),
                user.roles()
        );
    }

}
