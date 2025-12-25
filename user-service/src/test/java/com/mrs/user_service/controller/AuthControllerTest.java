package com.mrs.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.user_service.dto.LoginUserRequest;
import com.mrs.user_service.dto.RegisterUserRequest;
import com.mrs.user_service.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Nested
    @DisplayName("POST /auth/register - Register User")
    class RegisterTests {

        @Test
        @DisplayName("Should return 201 when registering with valid data")
        void register_WithValidData_ReturnsCreated() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!"
            );

            doNothing().when(authService).register(any(RegisterUserRequest.class));

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(authService, times(1)).register(any(RegisterUserRequest.class));
        }

        @Test
        @DisplayName("Should return 500 when email already exists")
        void register_WithExistingEmail_ReturnsError() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "existing@example.com",
                    "Password123!"
            );

            doThrow(new IllegalArgumentException("Email already in use"))
                    .when(authService).register(any(RegisterUserRequest.class));

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 400 when name is blank")
        void register_WithBlankName_ReturnsBadRequest() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "",
                    "john@example.com",
                    "Password123!"
            );

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void register_WithBlankEmail_ReturnsBadRequest() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "",
                    "Password123!"
            );

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when email is invalid")
        void register_WithInvalidEmail_ReturnsBadRequest() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "not-an-email",
                    "Password123!"
            );

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is blank")
        void register_WithBlankPassword_ReturnsBadRequest() throws Exception {
            RegisterUserRequest request = new RegisterUserRequest(
                    "John Doe",
                    "john@example.com",
                    ""
            );

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/login - Login User")
    class LoginTests {

        @Test
        @DisplayName("Should return 200 and token when login is successful")
        void login_WithValidCredentials_ReturnsToken() throws Exception {
            LoginUserRequest request = new LoginUserRequest(
                    "john@example.com",
                    "Password123!"
            );

            String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token";
            when(authService.login(any(LoginUserRequest.class))).thenReturn(expectedToken);

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(expectedToken));

            verify(authService, times(1)).login(any(LoginUserRequest.class));
        }

        @Test
        @DisplayName("Should return 500 when credentials are invalid")
        void login_WithInvalidCredentials_ReturnsError() throws Exception {
            LoginUserRequest request = new LoginUserRequest(
                    "john@example.com",
                    "wrongpassword"
            );

            when(authService.login(any(LoginUserRequest.class)))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void login_WithBlankEmail_ReturnsBadRequest() throws Exception {
            LoginUserRequest request = new LoginUserRequest(
                    "",
                    "Password123!"
            );

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("Should return 400 when password is blank")
        void login_WithBlankPassword_ReturnsBadRequest() throws Exception {
            LoginUserRequest request = new LoginUserRequest(
                    "john@example.com",
                    ""
            );

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
