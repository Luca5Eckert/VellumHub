package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.ReactionChangedEvent;
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
class UserReactionConsumerEventTest {

    @Mock
    private ReactionChangedUseCase reactionChangedUseCase;

    @InjectMocks
    private UserReactionConsumerEvent userReactionConsumerEvent;

    @Captor
    private ArgumentCaptor<ReactionChangedCommand> commandCaptor;

    @Test
    @DisplayName("Should invoke ReactionChangedUseCase when reaction event is received")
    void shouldInvokeUseCaseOnReactionEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReactionChangedEvent event = new ReactionChangedEvent(userId, bookId, "VERY_POSITIVE");

        userReactionConsumerEvent.consume(event);

        verify(reactionChangedUseCase, times(1)).execute(any(ReactionChangedCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to ReactionChangedUseCase")
    void shouldPassCorrectDataToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReactionChangedEvent event = new ReactionChangedEvent(userId, bookId, "POSITIVE");

        userReactionConsumerEvent.consume(event);

        verify(reactionChangedUseCase).execute(commandCaptor.capture());
        ReactionChangedCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.reactionType()).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReactionChangedEvent event = new ReactionChangedEvent(userId, bookId, "NEGATIVE");
        doThrow(new RuntimeException("Profile not found")).when(reactionChangedUseCase).execute(any());

        assertThatThrownBy(() -> userReactionConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Profile not found");
    }
}
