package com.mrs.user_service.handler.auth;

import com.mrs.user_service.module.auth.domain.handler.LoginUserHandler;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.share.security.token.TokenProvider;
import com.mrs.user_service.share.security.user.UserDetailImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserHandlerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private Authentication authentication;

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

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, RoleUser.USER);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(tokenProvider.createToken(eq(email), eq(userId), any())).thenReturn(expectedToken);

        // Act
        String result = loginUserHandler.execute(email, password);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider, times(1)).createToken(eq(email), eq(userId), any());
    }

    @Test
    @DisplayName("Should throw exception when credentials are invalid")
    void shouldThrowException_WhenCredentialsAreInvalid() {
        // Arrange
        String email = "test@example.com";
        String wrongPassword = "wrongPassword";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () ->
                loginUserHandler.execute(email, wrongPassword)
        );

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
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

        UserDetailImpl adminUser = new UserDetailImpl(userId, email, password, RoleUser.ADMIN);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(adminUser);
        when(tokenProvider.createToken(eq(email), eq(userId), any())).thenReturn(expectedToken);

        // Act
        String result = loginUserHandler.execute(email, password);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result);
    }
}
