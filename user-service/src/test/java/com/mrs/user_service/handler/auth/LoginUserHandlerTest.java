package com.mrs.user_service.handler.auth;

import com.mrs.user_service.model.RoleUser;
import com.mrs.user_service.security.token.TokenProvider;
import com.mrs.user_service.security.user.UserDetailImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUserHandler Tests")
class LoginUserHandlerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private LoginUserHandler loginUserHandler;

    @Test
    @DisplayName("Should return token when credentials are valid")
    void execute_WithValidCredentials_ReturnsToken() {
        String email = "john@example.com";
        String password = "Password123!";
        UUID userId = UUID.randomUUID();

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, RoleUser.USER);
        Authentication authentication = mock(Authentication.class);
        
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.createToken(email, userId, userDetails.getAuthorities()))
                .thenReturn("jwt.token.here");

        String result = loginUserHandler.execute(email, password);

        assertThat(result).isEqualTo("jwt.token.here");
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider, times(1)).createToken(email, userId, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("Should throw exception when credentials are invalid")
    void execute_WithInvalidCredentials_ThrowsException() {
        String email = "john@example.com";
        String wrongPassword = "wrongpassword";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> loginUserHandler.execute(email, wrongPassword))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(tokenProvider, never()).createToken(any(), any(), any());
    }

    @Test
    @DisplayName("Should authenticate with correct email and password")
    void execute_AuthenticatesWithCorrectCredentials() {
        String email = "test@example.com";
        String password = "TestPass123!";
        UUID userId = UUID.randomUUID();

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, RoleUser.ADMIN);
        Authentication authentication = mock(Authentication.class);
        
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.createToken(anyString(), any(UUID.class), any()))
                .thenReturn("token");

        loginUserHandler.execute(email, password);

        verify(authenticationManager).authenticate(argThat(auth -> 
            auth.getPrincipal().equals(email) && auth.getCredentials().equals(password)
        ));
    }

    @Test
    @DisplayName("Should include user ID in token generation")
    void execute_IncludesUserIdInToken() {
        String email = "john@example.com";
        String password = "Password123!";
        UUID userId = UUID.randomUUID();

        UserDetailImpl userDetails = new UserDetailImpl(userId, email, password, RoleUser.USER);
        Authentication authentication = mock(Authentication.class);
        
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.createToken(anyString(), any(UUID.class), any()))
                .thenReturn("token");

        loginUserHandler.execute(email, password);

        verify(tokenProvider).createToken(eq(email), eq(userId), any());
    }
}
