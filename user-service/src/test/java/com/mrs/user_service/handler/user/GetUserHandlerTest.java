package com.mrs.user_service.handler.user;

import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.handler.GetUserHandler;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserHandler getUserHandler;

    @Test
    @DisplayName("Deve retornar o usuário quando o ID existir no banco")
    void execute_ShouldReturnUser_WhenIdExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserEntity mockUser = new UserEntity();
        mockUser.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Act
        UserEntity result = getUserHandler.execute(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário não for encontrado")
    void execute_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> getUserHandler.execute(userId))
                .isInstanceOf(com.mrs.user_service.module.user.application.exception.UserNotFoundException.class);
    }
}