package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GetReadingListUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @InjectMocks
    private GetReadingListUseCase getReadingListUseCase;

    private BookProgress readingProgress(UUID userId, int currentPage) {
        Book book = mock(Book.class);
        return BookProgress.create(userId, book, currentPage, ReadingStatus.READING, OffsetDateTime.now(), null);
    }

    @Nested
    class Execute {

        @Test
        void shouldReturnReadingListForUser() {
            UUID userId = UUID.randomUUID();
            List<BookProgress> expected = List.of(readingProgress(userId, 50), readingProgress(userId, 100));
            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(expected);

            List<BookProgress> result = getReadingListUseCase.execute(userId);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(bp -> bp.getReadingStatus() == ReadingStatus.READING);
            then(bookProgressRepository).should().findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING);
        }

        @Test
        void shouldReturnEmptyListWhenUserHasNoBooksInReading() {
            UUID userId = UUID.randomUUID();
            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(List.of());

            List<BookProgress> result = getReadingListUseCase.execute(userId);

            assertThat(result).isEmpty();
            then(bookProgressRepository).should().findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING);
        }

        @Test
        void shouldReturnProgressWithCorrectCurrentPage() {
            UUID userId = UUID.randomUUID();
            BookProgress progress = readingProgress(userId, 75);
            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(List.of(progress));

            List<BookProgress> result = getReadingListUseCase.execute(userId);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getCurrentPage()).isEqualTo(75);
            assertThat(result.getFirst().getReadingStatus()).isEqualTo(ReadingStatus.READING);
        }
    }
}
