package com.mrs.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.user_service.dto.CreateUserRequest;
import com.mrs.user_service.dto.UpdateUserRequest;
import com.mrs.user_service.dto.UserGetResponse;
import com.mrs.user_service.model.RoleUser;
import com.mrs.user_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Nested
    @DisplayName("POST /users - Create User")
    class CreateUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 201 when creating user with valid data as ADMIN")
        void createUser_WithValidDataAsAdmin_ReturnsCreated() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            doNothing().when(userService).create(any(CreateUserRequest.class));

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(userService, times(1)).create(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 when user without ADMIN role tries to create user")
        void createUser_WithoutAdminRole_ReturnsForbidden() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(userService, never()).create(any(CreateUserRequest.class));
        }

        @Test
        @DisplayName("Should return 401 when creating user without authentication")
        void createUser_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 400 when email is blank")
        void createUser_WithBlankEmail_ReturnsBadRequest() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "",
                    "Password123!",
                    RoleUser.USER
            );

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).create(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 400 when email is invalid")
        void createUser_WithInvalidEmail_ReturnsBadRequest() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "invalid-email",
                    "Password123!",
                    RoleUser.USER
            );

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).create(any(CreateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 400 when name is blank")
        void createUser_WithBlankName_ReturnsBadRequest() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                    "",
                    "john@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 400 when role is null")
        void createUser_WithNullRole_ReturnsBadRequest() throws Exception {
            String jsonWithNullRole = """
                    {
                        "name": "John Doe",
                        "email": "john@example.com",
                        "password": "Password123!",
                        "role": null
                    }
                    """;

            mockMvc.perform(post("/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonWithNullRole))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /users/{id} - Get User By ID")
    class GetUserByIdTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and user data when user exists")
        void getById_WhenUserExists_ReturnsUser() throws Exception {
            UUID userId = UUID.randomUUID();
            UserGetResponse response = new UserGetResponse(userId, "John Doe", "john@example.com");

            when(userService.get(userId)).thenReturn(response);

            mockMvc.perform(get("/users/{id}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.name").value("John Doe"))
                    .andExpect(jsonPath("$.email").value("john@example.com"));

            verify(userService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 500 when user not found")
        void getById_WhenUserNotFound_ReturnsNotFound() throws Exception {
            UUID userId = UUID.randomUUID();

            when(userService.get(userId)).thenThrow(new IllegalArgumentException("User not found by id"));

            mockMvc.perform(get("/users/{id}", userId))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void getById_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(get("/users/{id}", userId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /users - Get All Users")
    class GetAllUsersTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and list of users")
        void getAll_ReturnsUserList() throws Exception {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            List<UserGetResponse> users = List.of(
                    new UserGetResponse(userId1, "John Doe", "john@example.com"),
                    new UserGetResponse(userId2, "Jane Doe", "jane@example.com")
            );

            when(userService.getAll(0, 10)).thenReturn(users);

            mockMvc.perform(get("/users")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("John Doe"))
                    .andExpect(jsonPath("$[1].name").value("Jane Doe"));

            verify(userService, times(1)).getAll(0, 10);
        }

        @Test
        @WithMockUser
        @DisplayName("Should use default pagination values")
        void getAll_WithDefaultPagination_UsesDefaults() throws Exception {
            when(userService.getAll(0, 10)).thenReturn(List.of());

            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk());

            verify(userService, times(1)).getAll(0, 10);
        }
    }

    @Nested
    @DisplayName("PUT /users/{id} - Update User")
    class UpdateUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 204 when updating user as ADMIN")
        void updateUser_AsAdmin_ReturnsNoContent() throws Exception {
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doNothing().when(userService).update(eq(userId), any(UpdateUserRequest.class));

            mockMvc.perform(put("/users/{id}", userId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).update(eq(userId), any(UpdateUserRequest.class));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 when user without ADMIN role tries to update")
        void updateUser_WithoutAdminRole_ReturnsForbidden() throws Exception {
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            mockMvc.perform(put("/users/{id}", userId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(userService, never()).update(any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /users/{id} - Delete User")
    class DeleteUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 204 when deleting user as ADMIN")
        void deleteUser_AsAdmin_ReturnsNoContent() throws Exception {
            UUID userId = UUID.randomUUID();

            doNothing().when(userService).delete(userId);

            mockMvc.perform(delete("/users/{id}", userId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(userService, times(1)).delete(userId);
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 when user without ADMIN role tries to delete")
        void deleteUser_WithoutAdminRole_ReturnsForbidden() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(delete("/users/{id}", userId)
                            .with(csrf()))
                    .andExpect(status().isForbidden());

            verify(userService, never()).delete(any());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void deleteUser_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(delete("/users/{id}", userId)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
