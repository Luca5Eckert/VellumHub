package com.mrs.user_service.handler.user;

import com.mrs.user_service.module.user.application.dto.UpdateUserRequest;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.handler.UpdateUserHandler;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UpdateUserHandler updateUserHandler;

    @Test
    @DisplayName("Should update name and email successfully when data is valid")
    void execute_ShouldUpdateNameAndEmail_WhenValidDataProvided() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = UserEntity.builder()
                .id(userId)
                .name("Old Name")
                .email("old@example.com")
                .role(RoleUser.USER)
                .active(true)
                .version(1L)
                .build();

        UpdateUserRequest request = new UpdateUserRequest("New Name", "new@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        // Act
        updateUserHandler.execute(userId, request);

        // Assert
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertEquals("New Name", savedUser.getName());
        assertEquals("new@example.com", savedUser.getEmail());
        assertTrue(savedUser.isActive(), "User should remain active");
        assertEquals(1L, savedUser.getVersion(), "Version should be managed by JPA/Hibernate, not manually");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when user ID does not exist")
    void execute_ShouldThrowException_WhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("Name", "email@test.com");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> updateUserHandler.execute(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not call existsByEmail when the email in request is the same as current")
    void execute_ShouldNotCheckEmailUniqueness_WhenEmailIsUnchanged() {
        UUID userId = UUID.randomUUID();
        String currentEmail = "same@example.com";
        UserEntity user = UserEntity.builder()
                .id(userId)
                .email(currentEmail)
                .build();

        UpdateUserRequest request = new UpdateUserRequest("New Name", currentEmail);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        updateUserHandler.execute(userId, request);

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository).save(user);
    }
}