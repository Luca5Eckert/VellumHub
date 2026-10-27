package com.vellumhub.user_service.module.auth.application.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.vellumhub.user_service.module.auth.domain.service.AuthService;
import com.vellumhub.user_service.module.auth.application.dto.LoginExternalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerGoogleLegacyGateTest {
    @Test
    void disablesLegacyGoogleLoginOnlyWhenExplicitlyConfigured() {
        AuthService service = mock(AuthService.class);
        var controller = new AuthController(service);
        ReflectionTestUtils.setField(controller, "legacyGoogleWritesEnabled", false);
        assertThatThrownBy(() -> controller.login((LoginExternalRequest) null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.GONE));
        verifyNoInteractions(service);
    }

    @Test
    void defaultPolicyStillAllowsLegacyHandlerToRun() {
        AuthService service = mock(AuthService.class);
        when(service.login((LoginExternalRequest) null)).thenReturn("legacy-token-test");
        var controller = new AuthController(service);
        org.assertj.core.api.Assertions.assertThat(controller.login((LoginExternalRequest) null).getBody())
                .isEqualTo("legacy-token-test");
    }
}
