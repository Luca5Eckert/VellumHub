package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.exception.BookSnapshotNotFoundException;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.event.ReadingSessionStartedEvent;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.exception.ReadingSessionAlreadyActiveException;
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
class StartReadingSessionUseCaseTest {

    private static final String TOPIC = "reading-session-started";

    @Mock
    private ReadingSessionEntryRepository readingSessionEntryRepository;

    @Mock
    private BookSnapshotRepository bookSnapshotRepository;

    @Mock
    private RequestContext requestContext;

    @Mock
    private ReadingSessionEventPublisher<String, ReadingSessionStartedEvent> eventPublisher;

    @InjectMocks
    private StartReadingSessionUseCase startReadingSessionUseCase = new StartReadingSessionUseCase(
            readingSessionEntryRepository,
            bookSnapshotRepository,
            requestContext,
            eventPublisher,
            TOPIC
    );

    @Test
    @DisplayName("Should start reading session and publish started event")
    void shouldStartReadingSession() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 300);

        when(requestContext.getUserId()).thenReturn(userId);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(snapshot));
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());

        startReadingSessionUseCase.execute(bookId);

        ArgumentCaptor<ReadingSessionEntry> entryCaptor = ArgumentCaptor.forClass(ReadingSessionEntry.class);
        verify(readingSessionEntryRepository).save(entryCaptor.capture());

        ReadingSessionEntry savedEntry = entryCaptor.getValue();
        assertThat(savedEntry.getUserId()).isEqualTo(userId);
        assertThat(savedEntry.getBookSnapshot()).isEqualTo(snapshot);
        assertThat(savedEntry.getCurrentPage()).isZero();
        assertThat(savedEntry.getOccurredAt()).isNotNull();

        ArgumentCaptor<ReadingSessionStartedEvent> eventCaptor = ArgumentCaptor.forClass(ReadingSessionStartedEvent.class);
        verify(eventPublisher).publish(eq(TOPIC), eq(bookId.toString()), eventCaptor.capture());

        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(savedEntry.getSessionId());
        assertThat(eventCaptor.getValue().currentPage()).isZero();
        assertThat(eventCaptor.getValue().occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should allow reread when latest session is completed")
    void shouldAllowRereadAfterCompletion() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 300);
        ReadingSessionEntry completed = ReadingSessionEntry.completed(UUID.randomUUID(), userId, snapshot, 300);

        when(requestContext.getUserId()).thenReturn(userId);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(snapshot));
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(completed));

        startReadingSessionUseCase.execute(bookId);

        ArgumentCaptor<ReadingSessionEntry> entryCaptor = ArgumentCaptor.forClass(ReadingSessionEntry.class);
        verify(readingSessionEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getSessionId()).isNotEqualTo(completed.getSessionId());
    }

    @Test
    @DisplayName("Should throw when book snapshot does not exist")
    void shouldThrowWhenBookSnapshotDoesNotExist() {
        UUID bookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(requestContext.getUserId()).thenReturn(userId);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> startReadingSessionUseCase.execute(bookId))
                .isInstanceOf(BookSnapshotNotFoundException.class);

        verify(readingSessionEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw when there is an active session for the same book")
    void shouldThrowWhenActiveSessionAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(bookId, 300);
        ReadingSessionEntry active = ReadingSessionEntry.started(UUID.randomUUID(), userId, snapshot);

        when(requestContext.getUserId()).thenReturn(userId);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(snapshot));
        when(readingSessionEntryRepository.findLatestByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> startReadingSessionUseCase.execute(bookId))
                .isInstanceOf(ReadingSessionAlreadyActiveException.class);

        verify(readingSessionEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private BookSnapshot snapshot(UUID bookId, int pageCount) {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(bookId);
        snapshot.setPageCount(pageCount);
        return snapshot;
    }
}
