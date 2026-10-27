package com.vellumhub.auth.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Resolves a cryptographically verified upstream OIDC subject to one canonical UUIDv7. */
@Service
@ConditionalOnProperty(name = "vellum.google.enabled", havingValue = "true")
public class FederatedIdentityService {
    private final FederatedIdentityRepository identities;
    private final ProfileProvisioningClient profiles;
    private final UuidV7 ids;
    private final String expectedIssuer;

    public FederatedIdentityService(FederatedIdentityRepository identities,
            ProfileProvisioningClient profiles, UuidV7 ids,
            @Value("${vellum.google.issuer:https://accounts.google.com}") String expectedIssuer) {
        this.identities = identities;
        this.profiles = profiles;
        this.ids = ids;
        this.expectedIssuer = expectedIssuer;
    }

    public UUID resolve(OidcUser upstream) {
        // OidcUserService already verifies signature/nonce/audience/issuer.
        String issuer = upstream.getIssuer() == null ? "" : upstream.getIssuer().toString();
        String subject = upstream.getSubject();
        String email = upstream.getEmail();
        if (!expectedIssuer.equals(issuer) || subject == null || subject.isBlank()
                || subject.length() > 255 || !Boolean.TRUE.equals(upstream.getEmailVerified())
                || email == null || email.isBlank() || email.length() > 320) {
            throw denied();
        }
        email = email.strip().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw denied();
        }
        String name = upstream.getFullName();
        if (name == null || name.isBlank()) {
            name = "Google user";
        }
        name = name.strip();
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        final UUID userId;
        try {
            userId = identities.resolveOrCreate(issuer, subject, email, name, ids.next());
        } catch (FederatedIdentityRepository.FederationConflictException exception) {
            throw denied();
        }
        var identity = identities.state(userId);
        if ("PENDING_PROFILE".equals(identity.provisioning())) {
            try {
                profiles.create(userId, identity.name(), identity.email());
                identities.activate(userId); // CAS/idempotent on competing callbacks.
            } catch (Exception exception) {
                throw denied(); // Reconciler handles partial User commit.
            }
        }
        if (!identities.state(userId).active()) {
            throw denied();
        }
        return userId;
    }

    private OAuth2AuthenticationException denied() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_external_identity"),
                "External identity cannot be authenticated");
    }
}
