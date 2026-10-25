package com.vellumhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class FederatedIdentityServiceTest {
    private final FederatedIdentityRepository repo = mock(FederatedIdentityRepository.class);
    private final ProfileProvisioningClient profiles = mock(ProfileProvisioningClient.class);
    private final UuidV7 ids = mock(UuidV7.class);
    private final UUID id = UUID.fromString("019a0000-1111-7000-8000-000000000001");
    private FederatedIdentityService service;

    @BeforeEach
    void setup() {
        service = new FederatedIdentityService(repo, profiles, ids, "https://accounts.google.com");
        when(ids.next()).thenReturn(id);
    }

    @Test
    void verifiesSubjectAndProvisioningBeforeLogin() {
        when(repo.resolveOrCreate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(id);
        when(repo.state(id)).thenReturn(
                new FederatedIdentityRepository.IdentityState(false, "PENDING_PROFILE", "ada@example.org", "Ada"),
                new FederatedIdentityRepository.IdentityState(true, "ACTIVE", "ada@example.org", "Ada"));
        assertThat(service.resolve(upstream("subject-1", "ada@example.org", true))).isEqualTo(id);
        verify(repo).resolveOrCreate(eq("https://accounts.google.com"),
                eq("subject-1"), eq("ada@example.org"), eq("Ada"), eq(id));
        verify(profiles).create(id, "Ada", "ada@example.org");
        verify(repo).activate(id);
    }

    @Test
    void linkedIdentityDoesNotProvisionAgain() {
        when(repo.resolveOrCreate(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(id);
        when(repo.state(id)).thenReturn(
                new FederatedIdentityRepository.IdentityState(true, "ACTIVE", "ada@example.org", "Ada"));
        assertThat(service.resolve(upstream("subject-1", "ada@example.org", true))).isEqualTo(id);
        verifyNoInteractions(profiles);
    }

    @Test
    void rejectsUnverifiedEmailBeforeDatabaseOrProfileCalls() {
        assertThatThrownBy(() -> service.resolve(upstream("subject-1", "ada@example.org", false)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(repo, profiles);
    }

    @Test
    void pendingIdentityMustNotAuthenticateWhileProfileIsUnavailable() {
        when(repo.resolveOrCreate(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(id);
        when(repo.state(id)).thenReturn(
                new FederatedIdentityRepository.IdentityState(false, "PENDING_PROFILE", "ada@example.org", "Ada"));
        doThrow(new IllegalStateException("User unavailable")).when(profiles)
                .create(id, "Ada", "ada@example.org");
        assertThatThrownBy(() -> service.resolve(upstream("subject-1", "ada@example.org", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(repo, never()).activate(any());
    }

    private static DefaultOidcUser upstream(String subject, String email, boolean verified) {
        var issued = Instant.now();
        var token = new OidcIdToken("verified-upstream-id-token", issued, issued.plusSeconds(300),
                Map.of("iss", "https://accounts.google.com", "sub", subject, "aud", "client-id",
                       "email", email, "email_verified", verified, "name", "Ada"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
    }
}
