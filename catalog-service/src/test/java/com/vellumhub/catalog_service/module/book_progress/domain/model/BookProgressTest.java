package com.vellumhub.catalog_service.module.book_progress.domain.model;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookProgressTest {

    private Book book;
    private UUID userId;

    @BeforeEach
    void setUp() {
        book = mock(Book.class);
        when(book.getPageCount()).thenReturn(300);
        userId = UUID.randomUUID();
    }

    @Nested
    class Create {

        @Test
        void shouldCreateWithReadingStatusAndDefaultStartedAt() {
            BookProgress progress = BookProgress.create(userId, book, 50, ReadingStatus.READING, null, null);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.READING);
            assertThat(progress.getCurrentPage()).isEqualTo(50);
            assertThat(progress.getStartedAt()).isNotNull();
            assertThat(progress.getEndAt()).isNull();
        }

        @Test
        void shouldUseProvidedStartedAtWhenReadingAndStartedAtIsPresent() {
            OffsetDateTime startedAt = OffsetDateTime.now().minusDays(5);

            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, startedAt, null);

            assertThat(progress.getStartedAt()).isEqualTo(startedAt);
        }

        @Test
        void shouldForceCompletedStatusAndMaxPageWhenEndAtIsProvided() {
            OffsetDateTime endAt = OffsetDateTime.now();

            BookProgress progress = BookProgress.create(userId, book, 50, ReadingStatus.READING, null, endAt);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.COMPLETED);
            assertThat(progress.getCurrentPage()).isEqualTo(300);
            assertThat(progress.getEndAt()).isEqualTo(endAt);
        }

        @Test
        void shouldForceMaxPageWhenStatusIsCompletedAndEndAtIsAbsent() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.COMPLETED, null, null);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.COMPLETED);
            assertThat(progress.getCurrentPage()).isEqualTo(300);
        }

        @Test
        void shouldNullifyStartedAtWhenStatusIsWantToRead() {
            OffsetDateTime startedAt = OffsetDateTime.now();

            BookProgress progress = BookProgress.create(userId, book, 0, ReadingStatus.WANT_TO_READ, startedAt, null);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.WANT_TO_READ);
            assertThat(progress.getStartedAt()).isNull();
        }

        @Test
        void shouldAssignUserIdAndBook() {
            BookProgress progress = BookProgress.create(userId, book, 0, ReadingStatus.WANT_TO_READ, null, null);

            assertThat(progress.getUserId()).isEqualTo(userId);
            assertThat(progress.getBook()).isEqualTo(book);
        }

        @Test
        void shouldNotAssignIdOnCreate() {
            BookProgress progress = BookProgress.create(userId, book, 0, ReadingStatus.WANT_TO_READ, null, null);

            assertThat(progress.getId()).isNull();
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdatePageAndStatus() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, OffsetDateTime.now(), null);

            progress.update(ReadingStatus.READING, 150);

            assertThat(progress.getCurrentPage()).isEqualTo(150);
            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.READING);
        }

        @Test
        void shouldAutomaticallyCompleteWhenPageReachesPageCount() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, OffsetDateTime.now(), null);

            progress.update(ReadingStatus.READING, 300);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.COMPLETED);
            assertThat(progress.getCurrentPage()).isEqualTo(300);
        }

        @Test
        void shouldAutomaticallyCompleteWhenPageExceedsPageCount() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, OffsetDateTime.now(), null);

            progress.update(ReadingStatus.READING, 400);

            assertThat(progress.getReadingStatus()).isEqualTo(ReadingStatus.COMPLETED);
            assertThat(progress.getCurrentPage()).isEqualTo(300);
        }

        @Test
        void shouldThrowWhenPageIsNegative() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, OffsetDateTime.now(), null);

            assertThatThrownBy(() -> progress.update(ReadingStatus.READING, -1))
                    .isInstanceOf(BookProgressDomainException.class)
                    .hasMessage("Current page cannot be negative");
        }

        @Test
        void shouldAllowZeroPage() {
            BookProgress progress = BookProgress.create(userId, book, 10, ReadingStatus.READING, OffsetDateTime.now(), null);

            assertThatNoException().isThrownBy(() -> progress.update(ReadingStatus.READING, 0));
            assertThat(progress.getCurrentPage()).isEqualTo(0);
        }
    }
}