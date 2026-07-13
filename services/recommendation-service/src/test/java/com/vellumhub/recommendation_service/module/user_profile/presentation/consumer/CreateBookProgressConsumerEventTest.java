package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateBookProgressConsumerEventTest {

    @Mock
    private UpdateBookProgressUseCase updateBookProgressUseCase;

    @Mock
    private VellumHubMetrics metrics;

    @InjectMocks
    private CreateBookProgressConsumerEvent createBookProgressConsumerEvent;

    @Captor
    private ArgumentCaptor<UpdateBookProgressCommand> commandCaptor;

    @Test
    @DisplayName("Should consume create progress payload as an initial progress update")
    void shouldConsumeCreateProgressPayloadAsInitialProgressUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreateBookProgressEvent event = new CreateBookProgressEvent(UUID.randomUUID(), userId, bookId, "READING", 12);

        Method consume = CreateBookProgressConsumerEvent.class.getMethod("consume", CreateBookProgressEvent.class);
        consume.invoke(createBookProgressConsumerEvent, event);

        verify(updateBookProgressUseCase).execute(commandCaptor.capture());
        UpdateBookProgressCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.progress()).isEqualTo("READING");
        assertThat(command.oldPage()).isZero();
        assertThat(command.newPage()).isEqualTo(12);
    }
}
