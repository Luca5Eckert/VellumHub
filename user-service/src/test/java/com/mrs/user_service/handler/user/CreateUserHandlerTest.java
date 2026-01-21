package com.mrs.user_service.handler.user;

import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.handler.CreateUserHandler;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreateUserHandler createUserHandler;

    @Test
    @DisplayName("Deve salvar usuário com sucesso quando os dados forem válidos")
    void execute_ShouldSaveUser_WhenDataIsValid() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);

        // Act
        createUserHandler.execute(user);

        // Assert
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário for nulo")
    void execute_ShouldThrowException_WhenUserIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> createUserHandler.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User can't be null");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o e-mail já existir")
    void execute_ShouldThrowException_WhenEmailExists() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("existing@example.com");
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> createUserHandler.execute(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User's email already in use");

        verify(userRepository, never()).save(any());
    }
}