package com.mrs.user_service.handler.user;

import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.exception.UserNotUniqueException;
import com.mrs.user_service.module.user.domain.handler.CreateUserHandler;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CreateUserHandler createUserHandler;

    @Test
    @DisplayName("Deve salvar usuário com sucesso quando os dados forem válidos")
    void execute_ShouldSaveUser_WhenDataIsValid() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword("rawPassword");
        
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("encodedPassword");

        // Act
        createUserHandler.execute(user);

        // Assert
        verify(userRepository, times(1)).save(user);
        verify(passwordEncoder, times(1)).encode("rawPassword");
        assertEquals("encodedPassword", user.getPassword());
    }

    @Test
    @DisplayName("Deve lançar exceção quando o e-mail já existir")
    void execute_ShouldThrowException_WhenEmailExists() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("existing@example.com");
        user.setPassword("password");
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> createUserHandler.execute(user))
                .isInstanceOf(UserNotUniqueException.class)
                .hasMessage("User is not unique.");

        verify(userRepository, never()).save(any());
    }
}