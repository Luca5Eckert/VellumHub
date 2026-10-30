package com.vellumhub.auth.config;

import java.util.Set;
import com.vellumhub.security.jwt.OAuthScopes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ClientScopePolicyTest {
    @Test
    void defaultEnabledClientHasNoAdministrativeCapabilities() {
        assertThat(Set.of(OAuthScopes.DEFAULT_CLIENT_SCOPES.split(",")))
                .doesNotContain("user:write", "catalog:write", "catalog:requests:read");
    }

    @Test
    void rejectsUnknownClientCapabilitiesBeforeReadingOrWritingRegistrations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThatThrownBy(() -> new AuthorizationPersistenceConfig().registeredClientRepository(
                jdbc, true, "explicit-client", "https://client.example/callback",
                "https://client.example/", Set.of("user:read", "*")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown API scope");
        verifyNoInteractions(jdbc);
    }
}
