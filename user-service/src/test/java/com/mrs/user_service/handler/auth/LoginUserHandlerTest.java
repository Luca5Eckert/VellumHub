package com.mrs.user_service.handler.auth;

import com.mrs.user_service.module.auth.domain.handler.LoginUserHandler;
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
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        String email = "user@example.com";
        String password = "password123";
        UUID userId = UUID.randomUUID();
        String expectedToken = "jwt-token-12345";

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(tokenProvider.createToken(anyString(), any(UUID.class), anyList()))
                .thenReturn(expectedToken);

        // Act
        String token = loginUserHandler.execute(email, password);

        // Assert
        assertNotNull(token);
        assertEquals(expectedToken, token);
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider, times(1)).createToken(email, userId, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("Should throw exception when credentials are invalid")
    void shouldThrowException_WhenCredentialsAreInvalid() {
        // Arrange
        String email = "user@example.com";
        String password = "wrongpassword";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThrows(BadCredentialsException.class, () ->
                loginUserHandler.execute(email, password)
        );

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verifyNoInteractions(tokenProvider);
    }

    @Test
    @DisplayName("Should create authentication token with correct email and password")
    void shouldCreateAuthenticationToken_WithCorrectCredentials() {
        // Arrange
        String email = "test@example.com";
        String password = "securePassword";
        UUID userId = UUID.randomUUID();

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(tokenProvider.createToken(anyString(), any(UUID.class), anyList()))
                .thenReturn("token");

        // Act
        loginUserHandler.execute(email, password);

        // Assert
        verify(authenticationManager).authenticate(argThat(auth ->
                auth instanceof UsernamePasswordAuthenticationToken &&
                        email.equals(auth.getPrincipal()) &&
                        password.equals(auth.getCredentials())
        ));
    }

    @SuppressWarnings("unchecked")
    private Collection<? extends GrantedAuthority> anyList() {
        return any(Collection.class);
    }
}
