package com.vellumhub.user_service.module.auth.domain.handler;

import com.vellumhub.user_service.module.auth.domain.port.ExternalVerification;
import com.vellumhub.user_service.module.auth.domain.port.TokenProvider;
import com.vellumhub.user_service.module.auth.domain.service.ExternalIdentityBindingService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginExternalHandler {
    private final ExternalVerification verifier;
    private final ExternalIdentityBindingService bindings;
    private final TokenProvider tokens;

    public LoginExternalHandler(ExternalVerification verifier,
                                ExternalIdentityBindingService bindings,
                                TokenProvider tokens) {
        this.verifier = verifier;
        this.bindings = bindings;
        this.tokens = tokens;
    }

    public String handle(String externalToken) {
        var identity = verifier.authenticate(externalToken);
        var user = bindings.resolveForLogin(identity);
        return tokens.createToken(user.getEmail(), user.getId(), List.of(user.getRole().name()));
    }
}
