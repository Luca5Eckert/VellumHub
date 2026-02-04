package com.mrs.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.user_service.module.user.application.controller.UserController;
import com.mrs.user_service.module.user.application.dto.CreateUserRequest;
import com.mrs.user_service.module.user.application.dto.UpdateUserRequest;
import com.mrs.user_service.module.user.application.dto.UserGetResponse;
import com.mrs.user_service.module.user.application.exception.UserNotFoundException;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("POST /users - Create User")
    class CreateUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidRequest_shouldReturnCreated")
        void whenValidRequest_shouldReturnCreated() throws Exception {
            // Arrange
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "john.doe@example.com",
                    "SecurePassword123!",
                    RoleUser.USER
            );

            doNothing().when(userService).create(any(CreateUserRequest.class));

            // Act & Assert
            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(userService, times(1)).create(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenInvalidEmail_shouldReturnBadRequest")
        void whenInvalidEmail_shouldReturnBadRequest() throws Exception {
            // Arrange - invalid email format
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "invalid-email",
                    "SecurePassword123!",
                    RoleUser.USER
            );

            // Act & Assert
            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).create(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenBlankName_shouldReturnBadRequest")
        void whenBlankName_shouldReturnBadRequest() throws Exception {
            // Arrange - blank name
            CreateUserRequest request = new CreateUserRequest(
                    "",
                    "john.doe@example.com",
                    "SecurePassword123!",
                    RoleUser.USER
            );

            // Act & Assert
            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).create(any(CreateUserRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /users/{id} - Get User By ID")
    class GetUserByIdTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidId_shouldReturnUser")
        void whenValidId_shouldReturnUser() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UserGetResponse expectedResponse = new UserGetResponse(
                    userId,
                    "John Doe",
                    "john.doe@example.com"
            );

            when(userService.get(userId)).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(get("/users/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.name").value("John Doe"))
                    .andExpect(jsonPath("$.email").value("john.doe@example.com"));

            verify(userService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnBadRequest() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userService.get(userId)).thenThrow(new UserNotFoundException());

            // Act & Assert
            mockMvc.perform(get("/users/{id}", userId))
                    .andExpect(status().isBadRequest());

            verify(userService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenInvalidUuidFormat_shouldReturnBadRequest")
        void whenInvalidUuidFormat_shouldReturnBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/users/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).get(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("GET /users - Get All Users")
    class GetAllUsersTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidRequest_shouldReturnUserList")
        void whenValidRequest_shouldReturnUserList() throws Exception {
            // Arrange
            List<UserGetResponse> users = List.of(
                    new UserGetResponse(UUID.randomUUID(), "John Doe", "john@example.com"),
                    new UserGetResponse(UUID.randomUUID(), "Jane Doe", "jane@example.com")
            );

            when(userService.getAll(0, 10)).thenReturn(users);

            // Act & Assert
            mockMvc.perform(get("/users")
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
        @WithMockUser
        @DisplayName("whenDefaultPagination_shouldUseDefaults")
        void whenDefaultPagination_shouldUseDefaults() throws Exception {
            // Arrange
            when(userService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert - use default pagination values
            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk());

            verify(userService, times(1)).getAll(0, 10);
        }

        @Test
        @WithMockUser
        @DisplayName("whenEmptyList_shouldReturnEmptyArray")
        void whenEmptyList_shouldReturnEmptyArray() throws Exception {
            // Arrange
            when(userService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PUT /users/{id} - Update User")
    class UpdateUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidRequest_shouldReturnNoContent")
        void whenValidRequest_shouldReturnNoContent() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doNothing().when(userService).update(eq(userId), any(UpdateUserRequest.class));

            // Act & Assert
            mockMvc.perform(put("/users/{id}", userId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).update(eq(userId), any(UpdateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnBadRequest() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doThrow(new UserNotFoundException()).when(userService).update(eq(userId), any(UpdateUserRequest.class));

            // Act & Assert
            mockMvc.perform(put("/users/{id}", userId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /users/{id} - Delete User")
    class DeleteUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidId_shouldReturnNoContent")
        void whenValidId_shouldReturnNoContent() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            doNothing().when(userService).delete(userId);

            // Act & Assert
            mockMvc.perform(delete("/users/{id}", userId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).delete(userId);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenUserNotFound_shouldReturnBadRequest")
        void whenUserNotFound_shouldReturnBadRequest() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            doThrow(new UserNotFoundException()).when(userService).delete(userId);

            // Act & Assert
            mockMvc.perform(delete("/users/{id}", userId)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }
}
