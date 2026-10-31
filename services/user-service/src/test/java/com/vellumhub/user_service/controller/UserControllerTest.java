package com.vellumhub.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.user_service.module.user.application.controller.UserController;
import com.vellumhub.user_service.module.user.application.dto.UpdateUserRequest;
import com.vellumhub.user_service.module.user.application.dto.UserGetResponse;
import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.service.UserService;
import com.vellumhub.user_service.share.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for UserController.
 * Uses @WebMvcTest for isolated controller layer testing with MockMvc.
 * Tests cover HTTP endpoints and validation. Security tests require integration testing.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {
    private static org.springframework.test.web.servlet.request.RequestPostProcessor human(String role, String scope) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("grant_type", "authorization_code"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope));
    }


    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Nested
    @DisplayName("GET /users/{id} - Get User By ID")
    class GetUserByIdTests {

        @Test
        @DisplayName("whenValidId_shouldReturnUser")
        void whenValidId_shouldReturnUser() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UserGetResponse expectedResponse = new UserGetResponse(
                    userId,
                    "John Doe",
                    "john.doe@example.com"
            );

            when(authenticationService.getAuthenticatedUserId()).thenReturn(userId);
            when(userService.getOwned(userId, userId)).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(get("/users/{id}", userId).with(human("ADMIN", "user:read")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.name").value("John Doe"))
                    .andExpect(jsonPath("$.email").value("john.doe@example.com"));

            verify(userService, times(1)).getOwned(userId, userId);
        }

        @Test
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnNotFound() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(authenticationService.getAuthenticatedUserId()).thenReturn(userId);
            when(userService.getOwned(userId, userId)).thenThrow(new UserNotFoundException());

            // Act & Assert
            mockMvc.perform(get("/users/{id}", userId).with(human("ADMIN", "user:read")))
                    .andExpect(status().isNotFound());

            verify(userService, times(1)).getOwned(userId, userId);
        }

        @Test
        @DisplayName("whenInvalidUuidFormat_shouldReturnBadRequest")
        void whenInvalidUuidFormat_shouldReturnBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/users/{id}", "invalid-uuid").with(human("ADMIN", "user:read")))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).getOwned(any(UUID.class), any(UUID.class));
        }
    }

    @Nested
    @DisplayName("GET /users - Get All Users")
    class GetAllUsersTests {

        @Test
        @DisplayName("whenValidRequest_shouldReturnUserList")
        void whenValidRequest_shouldReturnUserList() throws Exception {
            // Arrange
            List<UserGetResponse> users = List.of(
                    new UserGetResponse(UUID.randomUUID(), "John Doe", "john@example.com"),
                    new UserGetResponse(UUID.randomUUID(), "Jane Doe", "jane@example.com")
            );

            when(userService.getAll(0, 10)).thenReturn(users);

            // Act & Assert
            mockMvc.perform(get("/users").with(human("ADMIN", "user:read"))
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("John Doe"))
                    .andExpect(jsonPath("$[1].name").value("Jane Doe"));

            verify(userService, times(1)).getAll(0, 10);
        }

        @Test
        @DisplayName("whenDefaultPagination_shouldUseDefaults")
        void whenDefaultPagination_shouldUseDefaults() throws Exception {
            // Arrange
            when(userService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert - use default pagination values
            mockMvc.perform(get("/users").with(human("ADMIN", "user:read")))
                    .andExpect(status().isOk());

            verify(userService, times(1)).getAll(0, 10);
        }

        @Test
        @DisplayName("whenEmptyList_shouldReturnEmptyArray")
        void whenEmptyList_shouldReturnEmptyArray() throws Exception {
            // Arrange
            when(userService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/users").with(human("ADMIN", "user:read")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PUT /users/{id} - Update User")
    class UpdateUserTests {

        @Test
        @DisplayName("whenValidRequest_shouldReturnNoContent")
        void whenValidRequest_shouldReturnNoContent() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doNothing().when(userService).update(eq(userId), any(UpdateUserRequest.class));

            // Act & Assert
            mockMvc.perform(put("/users/{id}", userId).with(human("ADMIN", "user:write"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).update(eq(userId), any(UpdateUserRequest.class));
        }

        @Test
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnBadRequest() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doThrow(new UserNotFoundException()).when(userService).update(eq(userId), any(UpdateUserRequest.class));

            // Act & Assert
            mockMvc.perform(put("/users/{id}", userId).with(human("ADMIN", "user:write"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /users/{id} - Delete User")
    class DeleteUserTests {

        @Test
        @DisplayName("whenValidId_shouldReturnNoContent")
        void whenValidId_shouldReturnNoContent() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            doNothing().when(userService).delete(userId);

            // Act & Assert
            mockMvc.perform(delete("/users/{id}", userId).with(human("ADMIN", "user:write"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).delete(userId);
        }

        @Test
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnBadRequest() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            doThrow(new UserNotFoundException()).when(userService).delete(userId);

            // Act & Assert
            mockMvc.perform(delete("/users/{id}", userId).with(human("ADMIN", "user:write"))
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
