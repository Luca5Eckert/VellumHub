package com.mrs.user_service.handler.auth;

import com.mrs.user_service.module.auth.domain.handler.RegisterUserHandler;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    @InjectMocks
    private RegisterUserHandler registerUserHandler;

    @Test
    @DisplayName("Should register user successfully when valid data is provided")
    void shouldRegisterUser_WhenValidData() {
        // Arrange
        String rawPassword = "StrongPassword123!";
        String encodedPassword = "encoded-password-hash";

        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword(rawPassword);

        RuleResult validResult = new RuleResult(true);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordValidator.validate(any())).thenReturn(validResult);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // Act
        registerUserHandler.execute(user);

        // Assert
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertEquals(encodedPassword, savedUser.getPassword());
        assertEquals("test@example.com", savedUser.getEmail());
    }

    @Test
    @DisplayName("Should throw exception when user is null")
    void shouldThrowException_WhenUserIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> registerUserHandler.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User can't be null");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(passwordValidator);
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowException_WhenEmailExists() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("existing@example.com");
        user.setPassword("Password123!");

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> registerUserHandler.execute(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(passwordValidator);
    }

    @Test
    @DisplayName("Should throw exception when password is invalid")
    void shouldThrowException_WhenPasswordIsInvalid() {
        // Arrange
        String weakPassword = "weak";

        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword(weakPassword);

        RuleResult invalidResult = new RuleResult(false);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordValidator.validate(any())).thenReturn(invalidResult);
        when(passwordValidator.getMessages(invalidResult)).thenReturn(
                java.util.List.of("Password must be at least 8 characters")
        );

        // Act & Assert
        assertThatThrownBy(() -> registerUserHandler.execute(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password invalid");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Should encode password before saving user")
    void shouldEncodePassword_BeforeSaving() {
        // Arrange
        String rawPassword = "MyPassword123!";
        String encodedPassword = "hashed-password-value";

        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setPassword(rawPassword);

        RuleResult validResult = new RuleResult(true);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordValidator.validate(any())).thenReturn(validResult);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // Act
        registerUserHandler.execute(user);

        // Assert
        verify(passwordEncoder, times(1)).encode(rawPassword);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertEquals(encodedPassword, savedUser.getPassword());
        assertNotEquals(rawPassword, savedUser.getPassword());
    }
}
