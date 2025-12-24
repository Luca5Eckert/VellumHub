package com.mrs.user_service.handler.user;

import com.mrs.user_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeleteUserHandler deleteUserHandler;

    @Test
    @DisplayName("Deve deletar usuário com sucesso quando o ID existir")
    void execute_ShouldDeleteUser_WhenIdExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        // Act
        deleteUserHandler.execute(userId);

        // Assert
        verify(userRepository, times(1)).existsById(userId);
        verify(userRepository, times(1)).deleteById(userId);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário não for encontrado")
    void execute_ShouldThrowException_WhenUserDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> deleteUserHandler.execute(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not exist");

        // Garante que o delete nunca foi chamado após a falha do check
        verify(userRepository, never()).deleteById(any());
    }
}