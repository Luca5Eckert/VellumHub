package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.consumer;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.event.UpdateBookProgressEvent;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateReadingProgressEventConsumerTest {

    @Mock
    private CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;

    @Mock
    private VellumHubMetrics metrics;

    @InjectMocks
    private UpdateReadingProgressEventConsumer consumer;

    @Captor
    private ArgumentCaptor<CreateReadingSessionEntryCommand> commandCaptor;

    @Test
    @DisplayName("Should map update progress event IDs to the reading session command")
    void shouldMapUpdateProgressEventIdsToCommand() {
        UUID bookProgressId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdateBookProgressEvent event = new UpdateBookProgressEvent(bookProgressId, userId, bookId, "READING", 12, 90);

        consumer.consume(event);

        verify(createReadingSessionEntryUseCase).execute(commandCaptor.capture());
        CreateReadingSessionEntryCommand command = commandCaptor.getValue();

        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.bookProgressId()).isEqualTo(bookProgressId);
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.type()).isEqualTo("READING");
        assertThat(command.pageRead()).isEqualTo(90);
    }
}
