package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import com.vellumhub.engagement_service.share.port.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetReadingHistoryUseCaseTest {

    @Mock
    private ReadingSessionEntryRepository readingSessionEntryRepository;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private GetReadingHistoryUseCase getReadingHistoryUseCase;

    @Test
    @DisplayName("Should return reading history for authenticated user and book")
    void shouldReturnReadingHistory() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(bookId);
        snapshot.setPageCount(120);

        List<ReadingSessionEntry> history = List.of(
                ReadingSessionEntry.started(UUID.randomUUID(), userId, snapshot),
                ReadingSessionEntry.progressed(UUID.randomUUID(), userId, snapshot, 40)
        );

        when(requestContext.getUserId()).thenReturn(userId);
        when(readingSessionEntryRepository.findAllByUserIdAndBookId(userId, bookId)).thenReturn(history);

        List<ReadingSessionEntry> result = getReadingHistoryUseCase.execute(bookId);

        assertThat(result).containsExactlyElementsOf(history);
        verify(readingSessionEntryRepository).findAllByUserIdAndBookId(userId, bookId);
    }
}
