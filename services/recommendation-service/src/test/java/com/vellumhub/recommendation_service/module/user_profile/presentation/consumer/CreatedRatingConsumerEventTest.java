package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedRatingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatedRatingConsumerEventTest {

    @Mock
    private UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    @InjectMocks
    private CreatedRatingConsumerEvent createdRatingConsumerEvent;

    @Captor
    private ArgumentCaptor<UpdateUserProfileWithRatingCommand> commandCaptor;

    @Test
    @DisplayName("Should invoke UpdateUserProfileWithRatingUseCase when rating event is received")
    void shouldInvokeUseCaseOnRatingEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreatedRatingEvent event = new CreatedRatingEvent(userId, bookId, 4);

        createdRatingConsumerEvent.consume(event);

        verify(updateUserProfileWithRatingUseCase, times(1)).execute(any(UpdateUserProfileWithRatingCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to UpdateUserProfileWithRatingUseCase")
    void shouldPassCorrectDataToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreatedRatingEvent event = new CreatedRatingEvent(userId, bookId, 5);

        createdRatingConsumerEvent.consume(event);

        verify(updateUserProfileWithRatingUseCase).execute(commandCaptor.capture());
        UpdateUserProfileWithRatingCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.newStars()).isEqualTo(5);
        assertThat(command.oldStars()).isEqualTo(0);
        assertThat(command.isNewRating()).isFalse();
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreatedRatingEvent event = new CreatedRatingEvent(userId, bookId, 3);
        doThrow(new RuntimeException("Profile not found")).when(updateUserProfileWithRatingUseCase).execute(any());

        assertThatThrownBy(() -> createdRatingConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Profile not found");
    }
}
