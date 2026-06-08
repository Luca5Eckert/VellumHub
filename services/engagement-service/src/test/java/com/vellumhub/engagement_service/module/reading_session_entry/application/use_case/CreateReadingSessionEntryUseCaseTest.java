package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import com.vellumhub.engagement_service.share.port.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateReadingSessionEntryUseCaseTest {

    @Mock
    private ReadingSessionEntryRepository readingSessionEntryRepository;

    @Mock
    private BookSnapshotRepository bookSnapshotRepository;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private CreateReadingSessionEntryUseCase useCase;

    @Captor
    private ArgumentCaptor<ReadingSessionEntry> entryCaptor;

    @Test
    @DisplayName("Should use the event user id instead of request context")
    void shouldUseEventUserIdInsteadOfRequestContext() throws Exception {
        UUID bookId = UUID.randomUUID();
        UUID bookProgressId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BookSnapshot bookSnapshot = new BookSnapshot(bookId);
        CreateReadingSessionEntryCommand command = CreateReadingSessionEntryCommand.class
                .getDeclaredConstructor(UUID.class, UUID.class, UUID.class, String.class, int.class)
                .newInstance(bookId, bookProgressId, userId, "READING", 90);

        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(bookSnapshot));

        useCase.execute(command);

        verify(readingSessionEntryRepository).save(entryCaptor.capture());
        ReadingSessionEntry savedEntry = entryCaptor.getValue();

        assertThat(savedEntry.getBookSnapshot()).isEqualTo(bookSnapshot);
        assertThat(savedEntry.getReadingSessionId()).isEqualTo(bookProgressId);
        assertThat(savedEntry.getUserId()).isEqualTo(userId);
        assertThat(savedEntry.getType()).isEqualTo("READING");
        assertThat(savedEntry.getPageRead()).isEqualTo(90);
        verifyNoInteractions(requestContext);
    }
}
