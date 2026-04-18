package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.event.ReadingProgressUpdatedEvent;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.exception.InvalidReadingProgressException;
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
class UpdateReadingProgressUseCaseTest {

    private static final String TOPIC = "reading-progress-updated";

    @Mock
    private ReadingSessionEntryRepository readingSessionEntryRepository;

    @Mock
    private RequestContext requestContext;

    @Mock
    private ReadingSessionEventPublisher<String, ReadingProgressUpdatedEvent> eventPublisher;

    @InjectMocks
    private UpdateReadingProgressUseCase updateReadingProgressUseCase = new UpdateReadingProgressUseCase(
            readingSessionEntryRepository,
            requestContext,
            eventPublisher,
            TOPIC
    );

    @Test
    @DisplayName("Should append progress event and publish Kafka event")
    void shouldAppendProgressEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 320);
        UUID sessionId = UUID.randomUUID();
        ReadingSessionEntry started = ReadingSessionEntry.started(sessionId, userId, snapshot);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(started));

        updateReadingProgressUseCase.execute(bookId, 120);

        ArgumentCaptor<ReadingSessionEntry> entryCaptor = ArgumentCaptor.forClass(ReadingSessionEntry.class);
        verify(readingSessionEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(entryCaptor.getValue().getCurrentPage()).isEqualTo(120);

        ArgumentCaptor<ReadingProgressUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ReadingProgressUpdatedEvent.class);
        verify(eventPublisher).publish(eq(TOPIC), eq(bookId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(eventCaptor.getValue().currentPage()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should throw when there is no active session")
    void shouldThrowWhenThereIsNoActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateReadingProgressUseCase.execute(bookId, 10))
                .isInstanceOf(ReadingSessionNotActiveException.class);

        verify(readingSessionEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw when session is already completed")
    void shouldThrowWhenSessionIsCompleted() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 320);
        ReadingSessionEntry completed = ReadingSessionEntry.completed(UUID.randomUUID(), userId, snapshot, 320);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> updateReadingProgressUseCase.execute(bookId, 200))
                .isInstanceOf(ReadingSessionNotActiveException.class);
    }

    @Test
    @DisplayName("Should throw when page exceeds snapshot page count")
    void shouldThrowWhenPageExceedsBookPageCount() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 100);
        ReadingSessionEntry started = ReadingSessionEntry.started(UUID.randomUUID(), userId, snapshot);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> updateReadingProgressUseCase.execute(bookId, 101))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    @Test
    @DisplayName("Should throw when progress does not advance")
    void shouldThrowWhenProgressDoesNotAdvance() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 100);
        ReadingSessionEntry progressed = ReadingSessionEntry.progressed(UUID.randomUUID(), userId, snapshot, 40);

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(progressed));

        assertThatThrownBy(() -> updateReadingProgressUseCase.execute(bookId, 40))
                .isInstanceOf(InvalidReadingProgressException.class);
    }

    private BookSnapshot snapshot(UUID bookId, int pageCount) {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(bookId);
        snapshot.setPageCount(pageCount);
        return snapshot;
    }
}
