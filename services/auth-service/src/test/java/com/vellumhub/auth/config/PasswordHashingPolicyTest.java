package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHashingPolicyTest {
    private final AuthorizationPersistenceConfig config = new AuthorizationPersistenceConfig();

    @Test
    void usesArgon2idWithRandomSalt() {
        var encoder = config.passwordEncoder(19456, 2, 1);
        String first = encoder.encode("correct horse battery staple");
        String second = encoder.encode("correct horse battery staple");

        assertThat(first).startsWith("$argon2id$");
        assertThat(second).startsWith("$argon2id$").isNotEqualTo(first);
        assertThat(encoder.matches("correct horse battery staple", first)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
    }

    @Test
    void rejectsParametersBelowSecurityFloor() {
        assertThatThrownBy(() -> config.passwordEncoder(16384, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.passwordEncoder(19456, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.passwordEncoder(19456, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
