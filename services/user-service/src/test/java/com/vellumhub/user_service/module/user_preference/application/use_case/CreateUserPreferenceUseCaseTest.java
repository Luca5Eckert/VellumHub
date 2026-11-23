package com.vellumhub.user_service.module.user_preference.application.use_case;

import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import com.vellumhub.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserPreferenceUseCaseTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PreferenceOutbox outbox;

    private CreateUserPreferenceUseCase createUserPreferenceUseCase;

    private UUID userId;
    private UserEntity user;
    private CreateUserPreferenceCommand command;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = mock(UserEntity.class);
        command = new CreateUserPreferenceCommand(userId, List.of("FANTASY", "SCIENCE_FICTION"), "I love sci-fi books");
        createUserPreferenceUseCase = new CreateUserPreferenceUseCase(
                userPreferenceRepository,
                userRepository,
                outbox, false
        );
    }

    @Test
    void execute_whenUserDoesNotExist_shouldThrowUserNotFoundException() {
        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createUserPreferenceUseCase.execute(command))
                .isInstanceOf(UserNotFoundException.class);

        verify(userPreferenceRepository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void execute_whenPreferenceDoesNotExist_shouldCreateNewPreference() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.of(user));
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
    void execute_whenPreferenceAlreadyExists_shouldReplaceGenresAndPublishUpdatedPreference() {
        when(user.getId()).thenReturn(userId);

        UserPreference existing = UserPreference.builder()
                .user(user)
                .genres(List.of("ROMANCE"))
                .about("old about")
                .build();

        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        createUserPreferenceUseCase.execute(command);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        UserPreference saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getGenres()).isEqualTo(command.genres());
        assertThat(saved.getAbout()).isEqualTo(command.about());
        verify(outbox).enqueue(userId, 0, null, command.genres(), command.about());
    }

    @Test
    void execute_enqueuesSnapshotAfterSavingTheNextRevision() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.of(user));
        var existing = UserPreference.builder().user(user).revision(4).build();
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        createUserPreferenceUseCase.execute(command);

        var order = inOrder(userPreferenceRepository, outbox);
        order.verify(userPreferenceRepository).save(existing);
        order.verify(outbox).enqueue(userId, 5, null, command.genres(), command.about());
    }

    @Test
    void startsTheAvroSequenceIndependentlyOfExistingJsonRevisions() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.of(user));
        var existing = UserPreference.builder().user(user).revision(7).build();
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        var avro = new CreateUserPreferenceUseCase(userPreferenceRepository, userRepository, outbox, true);

        avro.execute(command);
        avro.execute(command);

        var order = inOrder(outbox);
        order.verify(outbox).enqueue(userId, 8, 0L, command.genres(), command.about());
        order.verify(outbox).enqueue(userId, 9, 1L, command.genres(), command.about());
    }

    @Test
    void refusesJsonAfterTheUserStartsTheAvroSequence() {
        when(user.getId()).thenReturn(userId);
        when(userRepository.findByIdForPreferenceUpdate(userId)).thenReturn(Optional.of(user));
        when(userPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserPreference.builder().user(user).avroRevision(0L).build()));

        assertThatThrownBy(() -> createUserPreferenceUseCase.execute(command))
                .hasMessage("A migrated preference cannot be published as JSON");
        verify(userPreferenceRepository, never()).save(any());
        verifyNoInteractions(outbox);
    }
}
