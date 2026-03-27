package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetReadingListUseCase Unit Tests")
class GetReadingListUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @InjectMocks
    private GetReadingListUseCase getReadingListUseCase;

    @Nested
    @DisplayName("Success scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should return list of books currently being read by user")
        void shouldReturnReadingListForUser() {
            // Given
            UUID userId = UUID.randomUUID();
            UUID bookId1 = UUID.randomUUID();
            UUID bookId2 = UUID.randomUUID();

            BookProgress progress1 = new BookProgress(bookId1, userId);
            progress1.setReadingStatus(ReadingStatus.READING);
            progress1.setCurrentPage(50);

            BookProgress progress2 = new BookProgress(bookId2, userId);
            progress2.setReadingStatus(ReadingStatus.READING);
            progress2.setCurrentPage(100);

            List<BookProgress> expectedList = List.of(progress1, progress2);

            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(expectedList);

            // When
            List<BookProgress> result = getReadingListUseCase.execute(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(BookProgress::getBookId).containsExactly(bookId1, bookId2);
            assertThat(result).allMatch(bp -> bp.getReadingStatus() == ReadingStatus.READING);
            then(bookProgressRepository).should().findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING);
        }

        @Test
        @DisplayName("Should return empty list when user has no books being read")
        void shouldReturnEmptyListWhenNoBooksBeingRead() {
            // Given
            UUID userId = UUID.randomUUID();

            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(Collections.emptyList());

            // When
            List<BookProgress> result = getReadingListUseCase.execute(userId);

            // Then
            assertThat(result).isEmpty();
            then(bookProgressRepository).should().findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING);
        }

        @Test
        @DisplayName("Should return only READING status books, not WANT_TO_READ or READ")
        void shouldReturnOnlyReadingStatusBooks() {
            // Given
            UUID userId = UUID.randomUUID();
            UUID bookId = UUID.randomUUID();

            BookProgress readingProgress = new BookProgress(bookId, userId);
            readingProgress.setReadingStatus(ReadingStatus.READING);
            readingProgress.setCurrentPage(75);

            // The repository will only return READING status books
            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(List.of(readingProgress));

            // When
            List<BookProgress> result = getReadingListUseCase.execute(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReadingStatus()).isEqualTo(ReadingStatus.READING);
            assertThat(result.get(0).getCurrentPage()).isEqualTo(75);
        }

        @Test
        @DisplayName("Should include current page progress in results")
        void shouldIncludeCurrentPageProgress() {
            // Given
            UUID userId = UUID.randomUUID();
            UUID bookId = UUID.randomUUID();
            int currentPage = 150;

            BookProgress progress = new BookProgress(bookId, userId);
            progress.setReadingStatus(ReadingStatus.READING);
            progress.setCurrentPage(currentPage);

            given(bookProgressRepository.findAllByUserIdAndReadingStatus(userId, ReadingStatus.READING))
                    .willReturn(List.of(progress));

            // When
            List<BookProgress> result = getReadingListUseCase.execute(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCurrentPage()).isEqualTo(currentPage);
        }
    }
}
