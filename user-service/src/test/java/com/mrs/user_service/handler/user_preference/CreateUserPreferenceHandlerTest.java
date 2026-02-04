package com.mrs.user_service.handler.user_preference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import com.mrs.user_service.module.user.domain.port.UserRepository;
import com.mrs.user_service.module.user_preference.domain.UserPreference;
import com.mrs.user_service.module.user_preference.domain.event.CreateUserPrefenceEvent;
import com.mrs.user_service.module.user_preference.domain.exception.UserPreferenceAlreadyExistDomainException;
import com.mrs.user_service.module.user_preference.domain.handler.CreateUserPreferenceHandler;
import com.mrs.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CreateUserPreferenceHandlerTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, CreateUserPrefenceEvent> kafkaTemplate;

    @InjectMocks
    private CreateUserPreferenceHandler handler;

    private UserPreference validPreference;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        validPreference = new UserPreference();
        validPreference.setUserId(userId);
    }

    @Test
    @DisplayName("Deve salvar preferência com sucesso quando dados forem válidos")
    void shouldSavePreferenceSuccess() {
        // Arrange
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.existsByUserId(userId)).thenReturn(false);

        // Act
        assertDoesNotThrow(() -> handler.execute(validPreference));

        // Assert
        verify(userPreferenceRepository, times(1)).save(validPreference);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário não existir")
    void shouldThrowExceptionWhenUserNotFound() {
        // Arrange
        when(userRepository.existsById(userId)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> handler.execute(validPreference));

        assertEquals("User not found", exception.getMessage());
        verify(userPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando uma prefencia de usuário já existir")
    void shouldThrowExceptionWhenUserPrefenceAlreadyExist() {
        // Arrange
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.existsByUserId(any(UUID.class))).thenReturn(true);

        // Act & Assert
        UserPreferenceAlreadyExistDomainException exception = assertThrows(UserPreferenceAlreadyExistDomainException.class,
                () -> handler.execute(validPreference));

        assertEquals("User already have a preference", exception.getMessage());
        verify(userPreferenceRepository, never()).save(any());
    }

}