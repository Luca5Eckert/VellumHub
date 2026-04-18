package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.event.ReadingSessionCompletedEvent;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.exception.ReadingSessionNotActiveException;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEventPublisher;
import com.vellumhub.engagement_service.share.port.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteReadingSessionUseCaseTest {

    private static final String TOPIC = "reading-session-completed";

    @Mock
    private ReadingSessionEntryRepository readingSessionEntryRepository;

    @Mock
    private RequestContext requestContext;

    @Mock
    private ReadingSessionEventPublisher<String, ReadingSessionCompletedEvent> eventPublisher;

    @InjectMocks
    private CompleteReadingSessionUseCase completeReadingSessionUseCase = new CompleteReadingSessionUseCase(
            readingSessionEntryRepository,
            requestContext,
            eventPublisher,
            TOPIC
    );

    @Test
    @DisplayName("Should complete active session using snapshot page count")
    void shouldCompleteActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 450);
        UUID sessionId = UUID.randomUUID();
        ReadingSessionEntry progressed = ReadingSessionEntry.progressed(sessionId, userId, snapshot, 200);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(progressed));

        completeReadingSessionUseCase.execute(bookId);

        ArgumentCaptor<ReadingSessionEntry> entryCaptor = ArgumentCaptor.forClass(ReadingSessionEntry.class);
        verify(readingSessionEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(entryCaptor.getValue().getCurrentPage()).isEqualTo(450);

        ArgumentCaptor<ReadingSessionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(ReadingSessionCompletedEvent.class);
        verify(eventPublisher).publish(eq(TOPIC), eq(bookId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().currentPage()).isEqualTo(450);
    }

    @Test
    @DisplayName("Should throw when there is no active session to complete")
    void shouldThrowWhenThereIsNoActiveSessionToComplete() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completeReadingSessionUseCase.execute(bookId))
                .isInstanceOf(ReadingSessionNotActiveException.class);

        verify(readingSessionEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw when latest event is already completed")
    void shouldThrowWhenLatestEventIsAlreadyCompleted() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 450);
        ReadingSessionEntry completed = ReadingSessionEntry.completed(UUID.randomUUID(), userId, snapshot, 450);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> completeReadingSessionUseCase.execute(bookId))
                .isInstanceOf(ReadingSessionNotActiveException.class);
    }

    private BookSnapshot snapshot(UUID bookId, int pageCount) {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(bookId);
        snapshot.setPageCount(pageCount);
        return snapshot;
    }
}
