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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityBindingServiceTest {
    private static final String ISSUER = "https://accounts.google.com";

    @Mock ExternalIdentityRepository identities;
    @Mock ExternalIdentityLock locks;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @InjectMocks ExternalIdentityBindingService bindings;

    private UserInfo google(String subject, String email) {
        return new UserInfo("Verified Google user", email, ISSUER, subject, true);
    }

    private UserEntity user(UUID id, String email, String password) {
        return UserEntity.builder().id(id).name("Owner").email(email)
                .password(password).role(RoleUser.USER).active(true).build();
    }

    @Test
    void logsIntoAlreadyBoundUserBySubjectEvenIfProviderEmailChanged() {
        var id = UUID.randomUUID();
        when(identities.findByIdentity(ISSUER, "fixed-subject"))
                .thenReturn(Optional.of(new ExternalIdentity(ISSUER, "fixed-subject", id)));
        when(users.findById(id)).thenReturn(Optional.of(user(id, "current@example.com", null)));

        var resolved = bindings.resolveForLogin(google("fixed-subject", "changed@example.com"));

        assertThat(resolved.getId()).isEqualTo(id);
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).link(any(), anyString());
    }

    @Test
    void refusesImplicitLinkToLegacyEmail() {
        when(users.existsByEmailIncludingDeleted("owner@example.com")).thenReturn(true);
        assertThatThrownBy(() -> bindings.resolveForLogin(google("unlinked-subject", "Owner@Example.com")))
                .isInstanceOf(ExternalIdentityConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void createsGoogleOnlyAccountAndAuditOnce() {
        UUID id = UUID.randomUUID();
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserEntity resolved = bindings.resolveForLogin(google("new-sub", "New@Example.com"));

        assertThat(resolved.getId()).isEqualTo(id);
        assertThat(resolved.getPassword()).isNull();
        assertThat(resolved.getEmail()).isEqualTo("new@example.com");
        verify(identities).link(new ExternalIdentity(ISSUER, "new-sub", id), "CREATED");
    }

    @Test
    void deniesUnverifiedAndForeignIssuer() {
        assertThatThrownBy(() -> bindings.resolveForLogin(
                new UserInfo("Name", "a@example.com", ISSUER, "s", false)))
                .isInstanceOf(AuthDomainException.class);
        assertThatThrownBy(() -> bindings.resolveForLogin(
                new UserInfo("Name", "a@example.com", "https://untrusted.example", "s", true)))
                .isInstanceOf(AuthDomainException.class);
        verifyNoInteractions(identities, users, locks);
    }

    @Test
    void linksOnlyAfterPasswordProofAndNeverStealsBoundSubject() {
        UUID ownerId = UUID.randomUUID(), anotherId = UUID.randomUUID();
        when(users.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner@example.com", "bcrypt")));
        when(passwords.matches("current-password", "bcrypt")).thenReturn(true);
        when(identities.findByIdentity(ISSUER, "taken"))
                .thenReturn(Optional.of(new ExternalIdentity(ISSUER, "taken", anotherId)));

        assertThatThrownBy(() -> bindings.link(ownerId, google("taken", "another@example.com"), "current-password"))
                .isInstanceOf(ExternalIdentityConflictException.class);
        verify(identities, never()).link(any(), anyString());
    }

    @Test
    void doesNotAllowSecondGoogleSubjectForSameLocalUser() {
        UUID ownerId = UUID.randomUUID();
        when(users.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner@example.com", "bcrypt")));
        when(passwords.matches("pwd", "bcrypt")).thenReturn(true);
        when(identities.findByUserAndIssuer(ownerId, ISSUER))
                .thenReturn(Optional.of(new ExternalIdentity(ISSUER, "existing", ownerId)));

        assertThatThrownBy(() -> bindings.link(ownerId, google("new", "owner@example.com"), "pwd"))
                .isInstanceOf(ExternalIdentityConflictException.class);
    }

    @Test
    void linkingSameSubjectToSameAccountIsIdempotent() {
        UUID ownerId = UUID.randomUUID();
        when(users.findById(ownerId)).thenReturn(Optional.of(user(ownerId, "owner@example.com", "bcrypt")));
        when(passwords.matches("pwd", "bcrypt")).thenReturn(true);
        when(identities.findByIdentity(ISSUER, "existing"))
                .thenReturn(Optional.of(new ExternalIdentity(ISSUER, "existing", ownerId)));
        bindings.link(ownerId, google("existing", "owner@example.com"), "pwd");
        verify(identities, never()).link(any(), anyString());
    }

    @Test
    void cannotLinkOrUnlinkGoogleOnlyUserWithoutPassword() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user(id, "google@example.com", null)));
        assertThatThrownBy(() -> bindings.link(id, google("sub", "google@example.com"), "anything"))
                .isInstanceOf(ExternalIdentityProofException.class);
        assertThatThrownBy(() -> bindings.unlink(id, "anything"))
                .isInstanceOf(ExternalIdentityProofException.class);
        verify(identities, never()).unlink(any());
    }

    @Test
    void passwordAuthenticatedAccountCanUnlinkWithoutLosingItsLocalCredential() {
        UUID id = UUID.randomUUID();
        var linked = new ExternalIdentity(ISSUER, "sub", id);
        when(users.findById(id)).thenReturn(Optional.of(user(id, "owner@example.com", "hash")));
        when(passwords.matches("proof", "hash")).thenReturn(true);
        when(identities.findByUserAndIssuer(id, ISSUER))
                .thenReturn(Optional.of(linked));
        bindings.unlink(id, "proof");
        verify(identities).unlink(linked);
    }
}
