package com.vellumhub.user_service.module.user_preference.application.use_case;

import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.domain.event.CreateUserPreferenceEvent;
import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import com.vellumhub.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserPreferenceUseCaseTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate;

    @InjectMocks
    private CreateUserPreferenceUseCase createUserPreferenceUseCase;

    private UUID userId;
    private UserEntity user;
    private CreateUserPreferenceCommand command;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = mock(UserEntity.class);
        command = new CreateUserPreferenceCommand(userId, List.of("FANTASY", "SCIENCE_FICTION"), "I love sci-fi books");
    }

    @Test
    void execute_whenUserDoesNotExist_shouldThrowUserNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUserPreferenceUseCase.execute(command))
                .isInstanceOf(UserNotFoundException.class);

        verify(userPreferenceRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void execute_whenPreferenceDoesNotExist_shouldCreateNewPreference() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        createUserPreferenceUseCase.execute(command);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        UserPreference saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getGenres()).isEqualTo(command.genres());
        assertThat(saved.getAbout()).isEqualTo(command.about());
    }

    @Test
    void execute_whenPreferenceAlreadyExists_shouldReuseExistingPreference() {
        when(user.getId()).thenReturn(userId);

        UserPreference existing = UserPreference.builder()
                .user(user)
                .genres(List.of("ROMANCE"))
                .about("old about")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        createUserPreferenceUseCase.execute(command);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        UserPreference saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getGenres()).isEqualTo(List.of("ROMANCE"));
        assertThat(saved.getAbout()).isEqualTo("old about");
    }

    @Test
    void execute_shouldPublishEventWithCorrectTopic() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        createUserPreferenceUseCase.execute(command);

        verify(kafkaTemplate).send(eq("create_user_preference"), eq(userId.toString()), any(CreateUserPreferenceEvent.class));
    }

    @Test
    void execute_shouldPublishEventWithCorrectPayload() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        createUserPreferenceUseCase.execute(command);

        ArgumentCaptor<CreateUserPreferenceEvent> captor = ArgumentCaptor.forClass(CreateUserPreferenceEvent.class);
        verify(kafkaTemplate).send(any(), any(), captor.capture());

        CreateUserPreferenceEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.genres()).isEqualTo(command.genres());
        assertThat(event.about()).isEqualTo(command.about());
    }

    @Test
    void execute_shouldSaveBeforePublishingEvent() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var inOrder = inOrder(userPreferenceRepository, kafkaTemplate);

        createUserPreferenceUseCase.execute(command);

        inOrder.verify(userPreferenceRepository).save(any());
        inOrder.verify(kafkaTemplate).send(any(), any(), any());
    }
}