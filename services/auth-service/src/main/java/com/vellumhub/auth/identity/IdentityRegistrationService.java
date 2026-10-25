package com.vellumhub.auth.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name = "vellum.identity.registration-enabled", havingValue = "true")
public class IdentityRegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityRegistrationService.class);
    private final OnboardingIdentityRepository identities;
    private final ProfileProvisioningClient profiles;
    private final UuidV7 ids;
    private final PasswordEncoder passwords;

    public IdentityRegistrationService(OnboardingIdentityRepository identities,
            ProfileProvisioningClient profiles, UuidV7 ids, PasswordEncoder passwords) {
        this.identities = identities;
        this.profiles = profiles;
        this.ids = ids;
        this.passwords = passwords;
    }

    public UUID register(String displayName, String email, String password) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 120
                || email == null || !email.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || email.length() > 320 || password == null || password.length() < 12
                || password.length() > 256) {
            throw new InvalidRegistrationException();
        }
        String login = email.strip().toLowerCase(Locale.ROOT);
        String name = displayName.strip();
        UUID id = ids.next();
        if (!identities.insertPending(id, name, login, passwords.encode(password))) {
            throw new IdentityAlreadyExistsException();
        }
        // If User is unreachable, the account is durable but cannot log in.
        // A scheduled reconciler retries using the same UUID; no distributed transaction.
        try {
            profiles.create(id, name, login);
            if (!identities.activate(id)) {
                throw new IllegalStateException("Pending identity disappeared");
            }
            return id;
        } catch (Exception error) {
            LOG.warn("Identity profile provisioning deferred: type={}", error.getClass().getSimpleName());
            throw new ProvisioningPendingException();
        }
    }

    @Scheduled(fixedDelayString = "${vellum.identity.reconcile-delay-ms:30000}",
               initialDelayString = "${vellum.identity.reconcile-delay-ms:30000}")
    public void reconcile() {
        for (var pending : identities.pending(50)) {
            try {
                if (pending.displayName() == null) {
                    continue; // Historical fixtures are not retried as onboarding accounts.
                }
                profiles.create(pending.id(), pending.displayName(), pending.email());
                identities.activate(pending.id());
            } catch (Exception error) {
                LOG.warn("Pending profile reconciliation deferred: type={}", error.getClass().getSimpleName());
            }
        }
    }

    public static class InvalidRegistrationException extends RuntimeException {}
    public static class IdentityAlreadyExistsException extends RuntimeException {}
    public static class ProvisioningPendingException extends RuntimeException {}
}
