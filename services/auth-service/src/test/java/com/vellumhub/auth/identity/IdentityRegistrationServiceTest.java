package com.vellumhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityRegistrationServiceTest {
    private final OnboardingIdentityRepository store = mock(OnboardingIdentityRepository.class);
    private final ProfileProvisioningClient profiles = mock(ProfileProvisioningClient.class);
    private final UuidV7 uuid = mock(UuidV7.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UUID id = UUID.fromString("019a0000-1111-7000-8000-000000000001");
    private IdentityRegistrationService service;

    @BeforeEach
    void before() {
        service = new IdentityRegistrationService(store, profiles, uuid, passwords);
        when(uuid.next()).thenReturn(id);
        when(passwords.encode(anyString())).thenReturn("{bcrypt}stored-only");
    }

    @Test
    void persistsPendingThenCreatesIdempotentProfileAndActivatesIdentity() {
        when(store.insertPending(eq(id), eq("Ada"), eq("ada@example.org"), anyString())).thenReturn(true);
        assertThat(service.register(" Ada ", "ADA@EXAMPLE.ORG", "long-test-password")).isEqualTo(id);
        var order = inOrder(store, profiles, store);
        order.verify(store).insertPending(id, "Ada", "ada@example.org", "{bcrypt}stored-only");
        order.verify(profiles).create(id, "Ada", "ada@example.org");
        order.verify(store).activate(id);
        verify(profiles, never()).create(any(), any(), eq("long-test-password"));
    }

    @Test
    void cannotActivateOrIssueLoginAfterPartialProfileFailure() {
        when(store.insertPending(eq(id), anyString(), anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("down")).when(profiles).create(eq(id), anyString(), anyString());
        assertThatThrownBy(() -> service.register("Ada", "ada@example.org", "long-test-password"))
                .isInstanceOf(IdentityRegistrationService.ProvisioningPendingException.class);
        verify(store, never()).activate(any());
        when(store.pending(50)).thenReturn(List.of(
                new OnboardingIdentityRepository.PendingIdentity(id, "Ada", "ada@example.org")));
        clearInvocations(profiles);
        service.reconcile();
        verify(store, never()).activate(any());
        reset(profiles);
        service.reconcile();
        verify(store).activate(id);
        verify(profiles).create(id, "Ada", "ada@example.org");
    }

    @Test
    void duplicateEmailDoesNotCreateSecondProfile() {
        when(store.insertPending(any(), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.register("Ada", "ada@example.org", "long-test-password"))
                .isInstanceOf(IdentityRegistrationService.IdentityAlreadyExistsException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void invalidRegistrationIsRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.register("Ada", "invalid", "password"))
                .isInstanceOf(IdentityRegistrationService.InvalidRegistrationException.class);
        verifyNoInteractions(store, profiles);
    }
}
