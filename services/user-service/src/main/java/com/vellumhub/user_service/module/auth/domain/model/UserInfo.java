package com.vellumhub.user_service.module.auth.domain.model;

/**
 * Provider identity established from a cryptographically verified Google ID token.
 * Email is only a profile attribute, never the identity lookup key.
 */
public record UserInfo(
        String name,
        String email,
        String issuer,
        String subject,
        boolean emailVerified
) {
}
