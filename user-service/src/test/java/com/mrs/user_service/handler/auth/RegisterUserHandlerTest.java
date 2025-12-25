package com.mrs.user_service.handler.auth;

import com.mrs.user_service.model.RoleUser;
import com.mrs.user_service.model.UserEntity;
import com.mrs.user_service.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterUserHandler Tests")
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
    @DisplayName("Should register user successfully with valid data")
    void execute_WithValidData_RegistersUser() {
        UserEntity user = UserEntity.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("Password123!")
                .role(RoleUser.USER)
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        
        RuleResult validResult = mock(RuleResult.class);
        when(validResult.isValid()).thenReturn(true);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(validResult);
        
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");

        registerUserHandler.execute(user);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encodedPassword");
    }

    @Test
    @DisplayName("Should throw exception when user is null")
    void execute_WithNullUser_ThrowsException() {
        assertThatThrownBy(() -> registerUserHandler.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User can't be null");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void execute_WithExistingEmail_ThrowsException() {
        UserEntity user = UserEntity.builder()
                .name("John Doe")
                .email("existing@example.com")
                .password("Password123!")
                .role(RoleUser.USER)
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registerUserHandler.execute(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when password is invalid")
    void execute_WithInvalidPassword_ThrowsException() {
        UserEntity user = UserEntity.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("weak")
                .role(RoleUser.USER)
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        
        RuleResult invalidResult = mock(RuleResult.class);
        when(invalidResult.isValid()).thenReturn(false);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(invalidResult);
        when(passwordValidator.getMessages(invalidResult)).thenReturn(java.util.List.of("Password too short"));

        assertThatThrownBy(() -> registerUserHandler.execute(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password invalid");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should encode password before saving")
    void execute_EncodesPasswordBeforeSaving() {
        UserEntity user = UserEntity.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("RawPassword123!")
                .role(RoleUser.USER)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        RuleResult validResult = mock(RuleResult.class);
        when(validResult.isValid()).thenReturn(true);
        when(passwordValidator.validate(any(PasswordData.class))).thenReturn(validResult);
        
        when(passwordEncoder.encode("RawPassword123!")).thenReturn("$2a$10$encodedHash");

        registerUserHandler.execute(user);

        verify(passwordEncoder, times(1)).encode("RawPassword123!");
        
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$encodedHash");
    }
}
