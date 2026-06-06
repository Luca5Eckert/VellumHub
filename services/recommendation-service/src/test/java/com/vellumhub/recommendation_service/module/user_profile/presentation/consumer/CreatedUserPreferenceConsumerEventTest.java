package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreateUserProfileUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedUserPreferenceEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatedUserPreferenceConsumerEventTest {

    @Mock
    private CreateUserProfileUseCase createUserProfileUseCase;

    @InjectMocks
    private CreatedUserPreferenceConsumerEvent createdUserPreferenceConsumerEvent;

    @Captor
    private ArgumentCaptor<CreatedUserProfileCommand> commandCaptor;

    @Test
    @DisplayName("Should invoke CreateUserProfileUseCase when user preference event is received")
    void shouldInvokeUseCaseOnPreferenceEvent() {
        UUID userId = UUID.randomUUID();
        CreatedUserPreferenceEvent event = new CreatedUserPreferenceEvent(userId, List.of("Fantasy", "Sci-Fi"), "Loves epic adventures");

        createdUserPreferenceConsumerEvent.consume(event);

        verify(createUserProfileUseCase, times(1)).execute(any(CreatedUserProfileCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to CreateUserProfileUseCase")
    void shouldPassCorrectDataToUseCase() {
        UUID userId = UUID.randomUUID();
        List<String> genres = List.of("Mystery", "Thriller");
        String about = "Enjoys suspenseful stories";
        CreatedUserPreferenceEvent event = new CreatedUserPreferenceEvent(userId, genres, about);

        createdUserPreferenceConsumerEvent.consume(event);

        verify(createUserProfileUseCase).execute(commandCaptor.capture());
        CreatedUserProfileCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.genres()).isEqualTo(genres);
        assertThat(command.about()).isEqualTo(about);
    }

    @Test
    @DisplayName("Should wrap and propagate exception when use case fails")
    void shouldWrapExceptionWhenUseCaseFails() {
        UUID userId = UUID.randomUUID();
        CreatedUserPreferenceEvent event = new CreatedUserPreferenceEvent(userId, List.of("Drama"), "Likes drama");
        doThrow(new RuntimeException("Embedding service unavailable")).when(createUserProfileUseCase).execute(any());

        assertThatThrownBy(() -> createdUserPreferenceConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class);
    }
}
