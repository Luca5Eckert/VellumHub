package com.mrs.user_service.service;

import com.mrs.user_service.dto.CreateUserRequest;
import com.mrs.user_service.dto.PageUser;
import com.mrs.user_service.dto.UpdateUserRequest;
import com.mrs.user_service.dto.UserGetResponse;
import com.mrs.user_service.handler.user.*;
import com.mrs.user_service.mapper.UserMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private CreateUserHandler createUserHandler;

    @Mock
    private DeleteUserHandler deleteUserHandler;

    @Mock
    private UpdateUserHandler updateUserHandler;

    @Mock
    private GetUserHandler getUserHandler;

    @Mock
    private GetAllUserHandler getAllUserHandler;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("create() method")
    class CreateTests {

        @Test
        @DisplayName("Should create user with correct data")
        void create_WithValidRequest_CreatesUser() {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "john@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            doNothing().when(createUserHandler).execute(any(UserEntity.class));

            userService.create(request);

            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(createUserHandler, times(1)).execute(userCaptor.capture());

            UserEntity capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getName()).isEqualTo("John Doe");
            assertThat(capturedUser.getEmail()).isEqualTo("john@example.com");
            assertThat(capturedUser.getPassword()).isEqualTo("Password123!");
            assertThat(capturedUser.getRole()).isEqualTo(RoleUser.USER);
        }

        @Test
        @DisplayName("Should create user with ADMIN role")
        void create_WithAdminRole_CreatesAdminUser() {
            CreateUserRequest request = new CreateUserRequest(
                    "Admin User",
                    "admin@example.com",
                    "AdminPass123!",
                    RoleUser.ADMIN
            );

            doNothing().when(createUserHandler).execute(any(UserEntity.class));

            userService.create(request);

            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(createUserHandler).execute(userCaptor.capture());

            assertThat(userCaptor.getValue().getRole()).isEqualTo(RoleUser.ADMIN);
        }

        @Test
        @DisplayName("Should propagate exception from handler")
        void create_WhenHandlerThrows_PropagatesException() {
            CreateUserRequest request = new CreateUserRequest(
                    "John Doe",
                    "existing@example.com",
                    "Password123!",
                    RoleUser.USER
            );

            doThrow(new IllegalArgumentException("Email already in use"))
                    .when(createUserHandler).execute(any(UserEntity.class));

            assertThatThrownBy(() -> userService.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email already in use");
        }
    }

    @Nested
    @DisplayName("delete() method")
    class DeleteTests {

        @Test
        @DisplayName("Should delete user by ID")
        void delete_WithValidId_DeletesUser() {
            UUID userId = UUID.randomUUID();
            doNothing().when(deleteUserHandler).execute(userId);

            userService.delete(userId);

            verify(deleteUserHandler, times(1)).execute(userId);
        }

        @Test
        @DisplayName("Should propagate exception when user not found")
        void delete_WhenUserNotFound_PropagatesException() {
            UUID userId = UUID.randomUUID();
            doThrow(new IllegalArgumentException("User not exist"))
                    .when(deleteUserHandler).execute(userId);

            assertThatThrownBy(() -> userService.delete(userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User not exist");
        }
    }

    @Nested
    @DisplayName("update() method")
    class UpdateTests {

        @Test
        @DisplayName("Should update user with correct data")
        void update_WithValidData_UpdatesUser() {
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = new UpdateUserRequest("Updated Name", "updated@example.com");

            doNothing().when(updateUserHandler).execute(userId, request);

            userService.update(userId, request);

            verify(updateUserHandler, times(1)).execute(userId, request);
        }
    }

    @Nested
    @DisplayName("get() method")
    class GetTests {

        @Test
        @DisplayName("Should return user response when user exists")
        void get_WhenUserExists_ReturnsUserResponse() {
            UUID userId = UUID.randomUUID();
            UserEntity userEntity = new UserEntity();
            userEntity.setId(userId);
            userEntity.setName("John Doe");
            userEntity.setEmail("john@example.com");

            UserGetResponse expectedResponse = new UserGetResponse(userId, "John Doe", "john@example.com");

            when(getUserHandler.execute(userId)).thenReturn(userEntity);
            when(userMapper.toGetResponse(userEntity)).thenReturn(expectedResponse);

            UserGetResponse result = userService.get(userId);

            assertThat(result).isEqualTo(expectedResponse);
            verify(getUserHandler, times(1)).execute(userId);
            verify(userMapper, times(1)).toGetResponse(userEntity);
        }

        @Test
        @DisplayName("Should propagate exception when user not found")
        void get_WhenUserNotFound_PropagatesException() {
            UUID userId = UUID.randomUUID();
            when(getUserHandler.execute(userId))
                    .thenThrow(new IllegalArgumentException("User not found by id"));

            assertThatThrownBy(() -> userService.get(userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User not found by id");
        }
    }

    @Nested
    @DisplayName("getAll() method")
    class GetAllTests {

        @Test
        @DisplayName("Should return list of user responses")
        void getAll_ReturnsUserResponses() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            UserEntity user1 = new UserEntity();
            user1.setId(userId1);
            user1.setName("John Doe");

            UserEntity user2 = new UserEntity();
            user2.setId(userId2);
            user2.setName("Jane Doe");

            Page<UserEntity> usersPage = new PageImpl<>(List.of(user1, user2));

            UserGetResponse response1 = new UserGetResponse(userId1, "John Doe", "john@example.com");
            UserGetResponse response2 = new UserGetResponse(userId2, "Jane Doe", "jane@example.com");

            when(getAllUserHandler.execute(any(PageUser.class))).thenReturn(usersPage);
            when(userMapper.toGetResponse(user1)).thenReturn(response1);
            when(userMapper.toGetResponse(user2)).thenReturn(response2);

            List<UserGetResponse> result = userService.getAll(0, 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(response1, response2);
        }

        @Test
        @DisplayName("Should return empty list when no users exist")
        void getAll_WhenNoUsers_ReturnsEmptyList() {
            Page<UserEntity> emptyPage = Page.empty();
            when(getAllUserHandler.execute(any(PageUser.class))).thenReturn(emptyPage);

            List<UserGetResponse> result = userService.getAll(0, 10);

            assertThat(result).isEmpty();
        }
    }
}
