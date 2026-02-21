package com.mrs.user_service.handler.auth;

import com.mrs.user_service.module.auth.domain.handler.LoginUserHandler;
import com.mrs.user_service.module.auth.domain.model.AuthenticatedUser;
import com.mrs.user_service.module.auth.domain.port.AuthenticatorPort;
import com.mrs.user_service.module.auth.domain.port.TokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserHandlerTest {

    @Mock
    private AuthenticatorPort authenticatorPort;

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private LoginUserHandler loginUserHandler;

    @Test
    @DisplayName("Should return token when credentials are valid")
    void shouldReturnToken_WhenCredentialsAreValid() {
        // Arrange
        String email = "test@example.com";
        String password = "password123";
        UUID userId = UUID.randomUUID();
        String expectedToken = "jwt-token-here";

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, email, List.of("USER"));

        when(authenticatorPort.authenticate(email, password)).thenReturn(authenticatedUser);
        when(tokenProvider.createToken(email, userId, List.of("USER"))).thenReturn(expectedToken);

        // Act
        String result = loginUserHandler.execute(email, password);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);

        verify(authenticatorPort, times(1)).authenticate(email, password);
        verify(tokenProvider, times(1)).createToken(email, userId, List.of("USER"));
    }

    @Test
    @DisplayName("Should throw exception when credentials are invalid")
    void shouldThrowException_WhenCredentialsAreInvalid() {
        // Arrange
        String email = "test@example.com";
        String wrongPassword = "wrongPassword";

        when(authenticatorPort.authenticate(email, wrongPassword))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () ->
                loginUserHandler.execute(email, wrongPassword)
        );

        verify(authenticatorPort, times(1)).authenticate(email, wrongPassword);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    @DisplayName("Should return token for admin user")
    void shouldReturnToken_ForAdminUser() {
        // Arrange
        String email = "admin@example.com";
        String password = "adminPassword";
        UUID userId = UUID.randomUUID();
        String expectedToken = "admin-jwt-token";

        AuthenticatedUser adminUser = new AuthenticatedUser(userId, email, List.of("ADMIN"));

        when(authenticatorPort.authenticate(email, password)).thenReturn(adminUser);
        when(tokenProvider.createToken(email, userId, List.of("ADMIN"))).thenReturn(expectedToken);

        // Act
        String result = loginUserHandler.execute(email, password);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);
    }
}
