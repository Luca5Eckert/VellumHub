package com.mrs.user_service.module.auth.domain.service;

import com.mrs.user_service.module.auth.application.dto.LoginUserRequest;
import com.mrs.user_service.module.auth.application.dto.RegisterUserRequest;
import com.mrs.user_service.module.auth.domain.handler.LoginUserHandler;
import com.mrs.user_service.module.auth.domain.handler.RegisterUserHandler;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for AuthService")
class AuthServiceTest {

    @Mock
    private LoginUserHandler loginUserHandler;

    @Mock
    private RegisterUserHandler registerUserHandler;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("Registration Logic")
    class RegisterTests {

        @Test
        @DisplayName("Should correctly map DTO to Entity and call RegisterUserHandler")
        void shouldRegisterUserSuccessfully() {
            // Arrange
            var request = new RegisterUserRequest(
                    "Alice Smith",
                    "alice@example.com",
                    "password123"
            );
            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

            // Act
            authService.register(request);

            // Assert
            verify(registerUserHandler, times(1)).execute(userCaptor.capture());

            UserEntity capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getName()).isEqualTo("Alice Smith");
            assertThat(capturedUser.getEmail()).isEqualTo("alice@example.com");
            assertThat(capturedUser.getPassword()).isEqualTo("password123");
            assertThat(capturedUser.getRole()).isEqualTo(RoleUser.USER);
        }
    }

    @Nested
    @DisplayName("Login Logic")
    class LoginTests {

        @Test
        @DisplayName("Should return a valid JWT token when credentials are valid")
        void shouldReturnTokenOnSuccessfulLogin() {
            // Arrange
            String mockToken = "eyJhbGciOiJIUzI1NiJ9.mockToken";
            var request = new LoginUserRequest("alice@example.com", "password123");

            when(loginUserHandler.execute(request.email(), request.password()))
                    .thenReturn(mockToken);

            // Act
            String resultToken = authService.login(request);

            // Assert
            assertThat(resultToken).isEqualTo(mockToken);
            verify(loginUserHandler, times(1)).execute(request.email(), request.password());
            verifyNoInteractions(registerUserHandler);
        }

        @Test
        @DisplayName("Should propagate exception if LoginUserHandler fails")
        void shouldPropagateExceptionWhenHandlerFails() {
            // Arrange
            var request = new LoginUserRequest("wrong@example.com", "wrong-pass");
            when(loginUserHandler.execute(any(), any()))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            // Act & Assert
            try {
                authService.login(request);
            } catch (Exception e) {
                assertThat(e.getMessage()).isEqualTo("Invalid credentials");
            }
        }
    }
}