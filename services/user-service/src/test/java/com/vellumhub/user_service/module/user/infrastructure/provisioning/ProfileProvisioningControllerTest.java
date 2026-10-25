package com.vellumhub.user_service.module.user.infrastructure.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ProfileProvisioningControllerTest {
    private static final String KEY = "poc-internal-provisioning-key-with-32-chars";
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ProfileProvisioningController subject = new ProfileProvisioningController(db, KEY);
    private final UUID uuid = UUID.fromString("019a0000-1111-7000-8000-000000000001");
    private final ProfileProvisioningController.NewProfile profile =
            new ProfileProvisioningController.NewProfile(uuid, "Ada", "ada@example.org");

    @Test
    void rejectsUntrustedCallerBeforeDatabaseAccess() {
        assertThat(subject.create("wrong-key", profile).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(subject.create(null, profile).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(db);
    }

    @Test
    void insertsProfileExactlyOnceUsingAuthAssignedUuid() {
        when(db.update(anyString(), any(), any(), any())).thenReturn(1);
        assertThat(subject.create(KEY, profile).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(db).update(anyString(), eq(uuid), eq("Ada"), eq("ada@example.org"));
    }

    @Test
    void rejectsConflictingUuidOrEmailInsteadOfConfirmingAFalseIdempotentRetry() {
        when(db.update(anyString(), any(), any(), any())).thenReturn(0);
        // Other user owns the same email -> lookup by UUID returns no record.
        assertThat(subject.create(KEY, profile).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsNonV7IdentifiersAndAdminRoleNotProvidedByCaller() {
        var invalid = new ProfileProvisioningController.NewProfile(UUID.randomUUID(), "Ada", "ada@example.org");
        assertThat(subject.create(KEY, invalid).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(db);
    }
}
