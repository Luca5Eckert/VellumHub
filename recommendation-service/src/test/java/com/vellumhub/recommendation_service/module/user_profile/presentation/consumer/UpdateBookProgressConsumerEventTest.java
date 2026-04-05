package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.UpdateBookProgressEvent;
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
class UpdateBookProgressConsumerEventTest {

    @Mock
    private UpdateBookProgressUseCase updateBookProgressUseCase;

    @InjectMocks
    private UpdateBookProgressConsumerEvent updateBookProgressConsumerEvent;

    @Captor
    private ArgumentCaptor<UpdateBookProgressCommand> commandCaptor;

    @Test
    @DisplayName("Should invoke UpdateBookProgressUseCase when progress event is received")
    void shouldInvokeUseCaseOnProgressEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdateBookProgressEvent event = new UpdateBookProgressEvent(userId, bookId, "READING", 0, 50);

        updateBookProgressConsumerEvent.consume(event);

        verify(updateBookProgressUseCase, times(1)).execute(any(UpdateBookProgressCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to UpdateBookProgressUseCase")
    void shouldPassCorrectDataToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdateBookProgressEvent event = new UpdateBookProgressEvent(userId, bookId, "COMPLETED", 100, 300);

        updateBookProgressConsumerEvent.consume(event);

        verify(updateBookProgressUseCase).execute(commandCaptor.capture());
        UpdateBookProgressCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.progress()).isEqualTo("COMPLETED");
        assertThat(command.oldPage()).isEqualTo(100);
        assertThat(command.newPage()).isEqualTo(300);
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdateBookProgressEvent event = new UpdateBookProgressEvent(userId, bookId, "READING", 0, 10);
        doThrow(new RuntimeException("Profile error")).when(updateBookProgressUseCase).execute(any());

        assertThatThrownBy(() -> updateBookProgressConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Profile error");
    }
}
