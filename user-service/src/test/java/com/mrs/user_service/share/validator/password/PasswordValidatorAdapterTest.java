package com.mrs.user_service.share.validator.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.passay.PasswordData;
import org.passay.RuleResult;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorAdapterTest {

    private final PasswordValidatorAdapter validator =
            new PasswordValidatorAdapter();

    @Test
    @DisplayName("Should accept a valid strong password")
    void shouldAcceptValidPassword() {
        RuleResult result = validator.validate(
                new PasswordData("Str0ng@Password")
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject password shorter than 8 characters")
    void shouldRejectShortPassword() {
        RuleResult result = validator.validate(
                new PasswordData("Aa1@a")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject password without uppercase letter")
    void shouldRejectPasswordWithoutUppercase() {
        RuleResult result = validator.validate(
                new PasswordData("lower1@case")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject password without lowercase letter")
    void shouldRejectPasswordWithoutLowercase() {
        RuleResult result = validator.validate(
                new PasswordData("UPPER1@CASE")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject password without digit")
    void shouldRejectPasswordWithoutDigit() {
        RuleResult result = validator.validate(
                new PasswordData("NoDigit@Here")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject password without special character")
    void shouldRejectPasswordWithoutSpecialCharacter() {
        RuleResult result = validator.validate(
                new PasswordData("NoSpecial1")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject password containing whitespace")
    void shouldRejectPasswordWithWhitespace() {
        RuleResult result = validator.validate(
                new PasswordData("Bad Pass1@")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject alphabetical sequence")
    void shouldRejectAlphabeticalSequence() {
        RuleResult result = validator.validate(
                new PasswordData("Abcde1@X")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject numeric sequence")
    void shouldRejectNumericSequence() {
        RuleResult result = validator.validate(
                new PasswordData("Test12345@A")
        );

        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should reject keyboard sequence (QWERTY)")
    void shouldRejectQwertySequence() {
        RuleResult result = validator.validate(
                new PasswordData("Qwerty1@A")
        );

        assertThat(result.isValid()).isFalse();
    }
}
