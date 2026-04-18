package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringReadingSessionEntryRepositoryAdapterTest {

    @Mock
    private JpaReadingSessionEntryRepository jpaReadingSessionEntryRepository;

    @InjectMocks
    private SpringReadingSessionEntryRepositoryAdapter adapter;

    @Test
    @DisplayName("Should delegate save to JPA repository")
    void shouldDelegateSave() {
        ReadingSessionEntry entry = new ReadingSessionEntry();

        adapter.save(entry);

        verify(jpaReadingSessionEntryRepository).save(entry);
    }

    @Test
    @DisplayName("Should delegate latest lookup to JPA repository")
    void shouldDelegateLatestLookup() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReadingSessionEntry entry = new ReadingSessionEntry();

        when(jpaReadingSessionEntryRepository.findFirstByUserIdAndBookSnapshot_BookIdOrderByOccurredAtDescIdDesc(userId, bookId))
                .thenReturn(Optional.of(entry));

        Optional<ReadingSessionEntry> result = adapter.findLatestByUserIdAndBookId(userId, bookId);

        assertThat(result).contains(entry);
    }

    @Test
    @DisplayName("Should delegate history lookup to JPA repository")
    void shouldDelegateHistoryLookup() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        List<ReadingSessionEntry> history = List.of(new ReadingSessionEntry());

        when(jpaReadingSessionEntryRepository.findAllByUserIdAndBookSnapshot_BookIdOrderByOccurredAtAscIdAsc(userId, bookId))
                .thenReturn(history);

        List<ReadingSessionEntry> result = adapter.findAllByUserIdAndBookId(userId, bookId);

        assertThat(result).containsExactlyElementsOf(history);
    }
}
