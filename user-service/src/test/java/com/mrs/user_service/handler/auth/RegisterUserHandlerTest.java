package com.mrs.user_service.handler.auth;

import com.mrs.user_service.module.auth.domain.exception.AuthDomainException;
import com.mrs.user_service.module.auth.domain.handler.RegisterUserHandler;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @DisplayName("Should register user when data is valid")
    void shouldRegisterUser_WhenDataIsValid() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword("ValidPassword123!");
        user.setName("Test User");
        user.setRole(RoleUser.USER);

        RuleResult validResult = mock(RuleResult.class);
        when(validResult.isValid()).thenReturn(true);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(validResult);
        when(passwordEncoder.encode("ValidPassword123!")).thenReturn("encodedPassword");

        // Act
        registerUserHandler.execute(user);

        // Assert
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertEquals("encodedPassword", savedUser.getPassword());
    }

    @Test
    @DisplayName("Should throw exception when user is null")
    void shouldThrowException_WhenUserIsNull() {
        // Act & Assert
        AuthDomainException exception = assertThrows(AuthDomainException.class, () ->
                registerUserHandler.execute(null)
        );

        assertEquals("User can't be null", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowException_WhenEmailAlreadyExists() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("existing@example.com");
        user.setPassword("ValidPassword123!");

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(true);

        // Act & Assert
        AuthDomainException exception = assertThrows(AuthDomainException.class, () ->
                registerUserHandler.execute(user)
        );

        assertEquals("Email already in use", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when password is invalid")
    void shouldThrowException_WhenPasswordIsInvalid() {
        // Arrange
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword("weak");

        RuleResult invalidResult = mock(RuleResult.class);
        when(invalidResult.isValid()).thenReturn(false);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(invalidResult);
        when(passwordValidator.getMessages(invalidResult)).thenReturn(java.util.List.of("Password must be at least 8 characters"));

        // Act & Assert
        AuthDomainException exception = assertThrows(AuthDomainException.class, () ->
                registerUserHandler.execute(user)
        );

        assertTrue(exception.getMessage().contains("Password invalid"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should encode password before saving")
    void shouldEncodePassword_BeforeSaving() {
        // Arrange
        String rawPassword = "MySecurePassword123!";
        String encodedPassword = "$2a$10$encodedPassword";

        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPassword(rawPassword);

        RuleResult validResult = mock(RuleResult.class);
        when(validResult.isValid()).thenReturn(true);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(validResult);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // Act
        registerUserHandler.execute(user);

        // Assert
        verify(passwordEncoder, times(1)).encode(rawPassword);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        assertEquals(encodedPassword, userCaptor.getValue().getPassword());
    }
}
