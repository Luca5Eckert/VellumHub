package com.vellumhub.engagement_service.module.reading_session_entry.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingSessionEntryTest {

    @Test
    @DisplayName("Should create started event with current page zero and timestamp")
    void shouldCreateStartedEvent() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(320);

        ReadingSessionEntry entry = ReadingSessionEntry.started(sessionId, userId, snapshot);

        assertThat(entry.getSessionId()).isEqualTo(sessionId);
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getBookSnapshot()).isEqualTo(snapshot);
        assertThat(entry.getEventType()).isEqualTo(ReadingSessionEventType.STARTED);
        assertThat(entry.getCurrentPage()).isZero();
        assertThat(entry.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create progress and completion events preserving session id")
    void shouldCreateProgressAndCompletionEvents() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BookSnapshot snapshot = snapshot(250);

        ReadingSessionEntry progress = ReadingSessionEntry.progressed(sessionId, userId, snapshot, 120);
        ReadingSessionEntry completed = ReadingSessionEntry.completed(sessionId, userId, snapshot, 250);

        assertThat(progress.getSessionId()).isEqualTo(sessionId);
        assertThat(progress.getEventType()).isEqualTo(ReadingSessionEventType.PROGRESSED);
        assertThat(progress.getCurrentPage()).isEqualTo(120);
        assertThat(progress.getOccurredAt()).isNotNull();

        assertThat(completed.getSessionId()).isEqualTo(sessionId);
        assertThat(completed.getEventType()).isEqualTo(ReadingSessionEventType.COMPLETED);
        assertThat(completed.getCurrentPage()).isEqualTo(250);
        assertThat(completed.getOccurredAt()).isNotNull();
    }

    private BookSnapshot snapshot(int pageCount) {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(UUID.randomUUID());
        snapshot.setPageCount(pageCount);
        return snapshot;
    }
}
