package com.vellumhub.user_service.module.auth.domain.service;

import com.vellumhub.user_service.module.auth.domain.exception.AuthDomainException;
import com.vellumhub.user_service.module.auth.domain.exception.ExternalIdentityConflictException;
import com.vellumhub.user_service.module.auth.domain.exception.ExternalIdentityProofException;
import com.vellumhub.user_service.module.auth.domain.model.ExternalIdentity;
import com.vellumhub.user_service.module.auth.domain.model.UserInfo;
import com.vellumhub.user_service.module.auth.domain.port.ExternalIdentityLock;
import com.vellumhub.user_service.module.auth.domain.port.ExternalIdentityRepository;
import com.vellumhub.user_service.module.user.domain.RoleUser;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Transactional boundary for verified provider identities. Cross-instance advisory locks
 * serialize first login/link/unlink, while database constraints enforce uniqueness.
 * This service does not fetch or verify external Google tokens inside DB transactions.
 */
@Service
public class ExternalIdentityBindingService {
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final ExternalIdentityRepository externalIdentities;
    private final ExternalIdentityLock locks;
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public ExternalIdentityBindingService(ExternalIdentityRepository externalIdentities,
                                          ExternalIdentityLock locks,
                                          UserRepository users,
                                          PasswordEncoder passwords) {
        this.externalIdentities = externalIdentities;
        this.locks = locks;
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public UserEntity resolveForLogin(UserInfo verified) {
        requireVerified(verified);
        String email = normalizedEmail(verified.email());
        locks.acquire(subjectKey(verified), "email:" + email);

        var existing = externalIdentities.findByIdentity(verified.issuer(), verified.subject());
        if (existing.isPresent()) {
            return requireActiveUser(existing.get().userId());
        }
        // No identity proof exists for an older account, regardless of matching email.
        // The owner must sign in locally and explicitly link the Google subject.
        if (users.existsByEmailIncludingDeleted(email)) {
            throw new ExternalIdentityConflictException();
        }
        String name = verified.name() == null || verified.name().isBlank()
                ? "Google user" : verified.name();
        UserEntity created = users.saveAndFlush(UserEntity.builder()
                .name(name).email(email).password(null).role(RoleUser.USER).active(true).build());
        externalIdentities.link(
                new ExternalIdentity(verified.issuer(), verified.subject(), created.getId()), "CREATED");
        return created;
    }

    @Transactional
    public void link(UUID userId, UserInfo verified, String currentPassword) {
        requireVerified(verified);
        locks.acquire(subjectKey(verified), userKey(userId, verified.issuer()));
        UserEntity owner = requireActiveUser(userId);
        requireCurrentPassword(owner, currentPassword);

        var subjectBinding = externalIdentities.findByIdentity(verified.issuer(), verified.subject());
        if (subjectBinding.isPresent()) {
            if (!Objects.equals(subjectBinding.get().userId(), userId)) {
                throw new ExternalIdentityConflictException();
            }
            return; // Idempotent duplicate linking; no duplicate audit event.
        }
        if (externalIdentities.findByUserAndIssuer(userId, verified.issuer()).isPresent()) {
            throw new ExternalIdentityConflictException();
        }
        externalIdentities.link(
                new ExternalIdentity(verified.issuer(), verified.subject(), userId), "LINKED");
    }

    @Transactional
    public void unlink(UUID userId, String currentPassword) {
        UserEntity owner = requireActiveUser(userId);
        requireCurrentPassword(owner, currentPassword);
        var current = externalIdentities.findByUserAndIssuer(userId, GOOGLE_ISSUER);
        if (current.isEmpty()) {
            return; // Idempotent for a password-authenticated account.
        }
        // Read again after locking to avoid acting on stale external-identity state.
        locks.acquire(subjectKey(current.get().issuer(), current.get().subject()),
                userKey(userId, GOOGLE_ISSUER));
        var locked = externalIdentities.findByUserAndIssuer(userId, GOOGLE_ISSUER);
        if (locked.isPresent() && locked.get().subject().equals(current.get().subject())) {
            externalIdentities.unlink(locked.get());
        } else if (locked.isPresent()) {
            throw new ExternalIdentityConflictException();
        }
    }

    private UserEntity requireActiveUser(UUID userId) {
        return users.findById(userId).filter(UserEntity::isActive)
                .orElseThrow(ExternalIdentityProofException::new);
    }

    private void requireCurrentPassword(UserEntity user, String password) {
        if (user.getPassword() == null || user.getPassword().isBlank()
                || password == null || !passwords.matches(password, user.getPassword())) {
            // Google-only accounts cannot unlink their last login method or link by bearer alone.
            throw new ExternalIdentityProofException();
        }
    }

    private void requireVerified(UserInfo verified) {
        if (verified == null || !verified.emailVerified()
                || !GOOGLE_ISSUER.equals(verified.issuer())
                || verified.subject() == null || verified.subject().isBlank()
                || verified.subject().length() > 255) {
            throw new AuthDomainException("Invalid external identity.");
        }
        normalizedEmail(verified.email());
    }

    private String normalizedEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AuthDomainException("Invalid external identity.");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String subjectKey(UserInfo verified) {
        return subjectKey(verified.issuer(), verified.subject());
    }

    private String subjectKey(String issuer, String subject) {
        return "subject:" + issuer + ":" + subject;
    }

    private String userKey(UUID userId, String issuer) {
        return "user:" + userId + ":" + issuer;
    }
}
