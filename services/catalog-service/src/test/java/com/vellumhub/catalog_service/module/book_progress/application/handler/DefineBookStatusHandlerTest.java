package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.application.event.ReadingProgressEventFactory;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import java.time.Instant;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefineBookStatusHandlerTest {

    @Mock
    private DefineBookStatusUseCase defineBookStatusUseCase;

    @Mock
    private OutboxWriter outboxWriter;

    @org.mockito.Spy
    private ReadingProgressEventFactory eventFactory = new ReadingProgressEventFactory();

    @InjectMocks
    private DefineBookStatusHandler handler;

    private UUID userId;
    private UUID bookId;
    private UUID bookProgressId;

    @BeforeEach
    void setUp() {
        bookProgressId = UUID.randomUUID();
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
    }

    @Nested
    class Handle {

        @Test
        void shouldBuildCommandFromRequestAndDelegateToUseCase() {
            OffsetDateTime startedAt = OffsetDateTime.now();
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 10, startedAt, null);
            CreateBookProgressEvent event = new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), bookProgressId, userId, bookId, "READING", 10);

            when(defineBookStatusUseCase.execute(any())).thenReturn(event);

            handler.handle(request, userId, bookId);

            ArgumentCaptor<DefineBookStatusCommand> captor = ArgumentCaptor.forClass(DefineBookStatusCommand.class);
            verify(defineBookStatusUseCase).execute(captor.capture());

            DefineBookStatusCommand command = captor.getValue();
            assertThat(command.userId()).isEqualTo(userId);
            assertThat(command.bookId()).isEqualTo(bookId);
            assertThat(command.readingStatus()).isEqualTo(ReadingStatus.READING);
            assertThat(command.initialPage()).isEqualTo(10);
            assertThat(command.startedAt()).isEqualTo(startedAt);
            assertThat(command.endAt()).isNull();
        }

        @Test
        void shouldPublishEventWithUserIdAsKeyToConfiguredTopic() {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);
            CreateBookProgressEvent event = new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), bookProgressId, userId, bookId, "READING", 0);

            when(defineBookStatusUseCase.execute(any())).thenReturn(event);

            handler.handle(request, userId, bookId);

            ArgumentCaptor<OutboxMessage> messages = ArgumentCaptor.forClass(OutboxMessage.class);
            verify(outboxWriter).append(messages.capture());
            assertThat(messages.getValue().topic()).isEqualTo("reading-progress-events");
            assertThat(messages.getValue().messageKey()).isEqualTo(bookProgressId.toString());
            assertThat(messages.getValue().revision()).isZero();
        }

        @Test
        void shouldPropagateExceptionFromUseCase() {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);

            when(defineBookStatusUseCase.execute(any())).thenThrow(new RuntimeException("use case failure"));

            assertThatThrownBy(() -> handler.handle(request, userId, bookId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("use case failure");

            verifyNoInteractions(outboxWriter);
        }

        @Test
        void shouldNotPublishEventWhenUseCaseThrows() {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);

            when(defineBookStatusUseCase.execute(any())).thenThrow(new RuntimeException());

            assertThatThrownBy(() -> handler.handle(request, userId, bookId))
                    .isInstanceOf(RuntimeException.class);

            verify(outboxWriter, never()).append(any());
        }
    }
}

