package com.mrs.user_service.service;

import com.mrs.user_service.dto.LoginUserRequest;
import com.mrs.user_service.dto.RegisterUserRequest;
import com.mrs.user_service.handler.auth.LoginUserHandler;
import com.mrs.user_service.handler.auth.RegisterUserHandler;
import com.mrs.user_service.model.RoleUser;
import com.mrs.user_service.model.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private LoginUserHandler loginUserHandler;

    @Mock
    private RegisterUserHandler registerUserHandler;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("register() method")
    class RegisterTests {

        @Test
        @DisplayName("Should register user with USER role")
        void register_WithValidRequest_RegistersUserWithUserRole() {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!"
            );

            doNothing().when(registerUserHandler).execute(any(UserEntity.class));

            authService.register(request);

            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(registerUserHandler, times(1)).execute(userCaptor.capture());

            UserEntity capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getName()).isEqualTo("John Doe");
            assertThat(capturedUser.getEmail()).isEqualTo("john@example.com");
            assertThat(capturedUser.getPassword()).isEqualTo("Password123!");
            assertThat(capturedUser.getRole()).isEqualTo(RoleUser.USER);
        }

        @Test
        @DisplayName("Should always assign USER role regardless of any implicit request")
        void register_AlwaysAssignsUserRole() {
            RegisterUserRequest request = new RegisterUserRequest(
                    "Admin Attempt",
                    "admin@example.com",
                    "AdminPass123!"
            );

            doNothing().when(registerUserHandler).execute(any(UserEntity.class));

            authService.register(request);

            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(registerUserHandler).execute(userCaptor.capture());

            assertThat(userCaptor.getValue().getRole()).isEqualTo(RoleUser.USER);
        }

        @Test
        @DisplayName("Should propagate exception when email already exists")
        void register_WithExistingEmail_PropagatesException() {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "existing@example.com",
                    "Password123!"
            );

            doThrow(new IllegalArgumentException("Email already in use"))
                    .when(registerUserHandler).execute(any(UserEntity.class));

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email already in use");
        }

        @Test
        @DisplayName("Should propagate exception when password is invalid")
        void register_WithInvalidPassword_PropagatesException() {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "john@example.com",
                    "weak"
            );

            doThrow(new IllegalArgumentException("Password invalid: Password must be at least 8 characters"))
                    .when(registerUserHandler).execute(any(UserEntity.class));

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Password invalid");
        }
    }

    @Nested
    @DisplayName("login() method")
    class LoginTests {

        @Test
        @DisplayName("Should return token when login is successful")
        void login_WithValidCredentials_ReturnsToken() {
            LoginUserRequest request = new LoginUserRequest(
                    "john@example.com",
                    "Password123!"
            );

            String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token";
            when(loginUserHandler.execute("john@example.com", "Password123!"))
                    .thenReturn(expectedToken);

            String result = authService.login(request);

            assertThat(result).isEqualTo(expectedToken);
            verify(loginUserHandler, times(1)).execute("john@example.com", "Password123!");
        }

        @Test
        @DisplayName("Should propagate exception when credentials are invalid")
        void login_WithInvalidCredentials_PropagatesException() {
            LoginUserRequest request = new LoginUserRequest(
                    "john@example.com",
                    "wrongpassword"
            );

            when(loginUserHandler.execute("john@example.com", "wrongpassword"))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Should call handler with correct email and password")
        void login_CallsHandlerWithCorrectParameters() {
            LoginUserRequest request = new LoginUserRequest(
                    "test@example.com",
                    "TestPass123!"
            );

            when(loginUserHandler.execute(anyString(), anyString())).thenReturn("token");

            authService.login(request);

            verify(loginUserHandler).execute("test@example.com", "TestPass123!");
        }
    }
}
