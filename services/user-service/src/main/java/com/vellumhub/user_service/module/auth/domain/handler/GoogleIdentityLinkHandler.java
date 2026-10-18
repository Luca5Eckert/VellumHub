package com.vellumhub.user_service.module.auth.domain.handler;

import com.vellumhub.user_service.module.auth.domain.port.ExternalVerification;
import com.vellumhub.user_service.module.auth.domain.service.ExternalIdentityBindingService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GoogleIdentityLinkHandler {
    private final ExternalVerification verifier;
    private final ExternalIdentityBindingService bindings;

    public GoogleIdentityLinkHandler(ExternalVerification verifier,
                                     ExternalIdentityBindingService bindings) {
        this.verifier = verifier;
        this.bindings = bindings;
    }

    public void link(UUID userId, String externalToken, String currentPassword) {
        bindings.link(userId, verifier.authenticate(externalToken), currentPassword);
    }

    public void unlink(UUID userId, String currentPassword) {
        bindings.unlink(userId, currentPassword);
    }
}
