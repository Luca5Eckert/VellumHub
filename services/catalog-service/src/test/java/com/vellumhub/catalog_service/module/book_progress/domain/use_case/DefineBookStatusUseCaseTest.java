package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.event.CreateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefineBookStatusUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private DefineBookStatusUseCase useCase;

    private UUID userId;
    private UUID bookId;
    private Book book;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        book = mock(Book.class);
    }

    @Nested
    class Execute {

        @Test
        void shouldSaveBookProgressAndReturnEvent() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.READING, 0, OffsetDateTime.now(), null
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
            when(bookProgressRepository.existsByUserIdAndBookIdAndIsActive(userId, bookId))
                    .thenReturn(false);

            CreateBookProgressEvent event = useCase.execute(command);

            ArgumentCaptor<BookProgress> captor = ArgumentCaptor.forClass(BookProgress.class);
            verify(bookProgressRepository).save(captor.capture());

            BookProgress saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getReadingStatus()).isEqualTo(ReadingStatus.READING);

            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.bookId()).isEqualTo(bookId);
            assertThat(event.progress()).isEqualTo(ReadingStatus.READING.name());
        }

        @Test
        void shouldThrowWhenBookNotFound() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.READING, 0, null, null
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BookNotFoundException.class);

            verifyNoInteractions(bookProgressRepository);
        }

        @Test
        void shouldThrowWhenUserAlreadyHasBookInReadingStatus() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.READING, 0, null, null
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
            when(bookProgressRepository.existsByUserIdAndBookIdAndIsActive(userId, bookId))
                    .thenReturn(true);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BookProgressDomainException.class)
                    .hasMessageContaining("READING");

            verify(bookProgressRepository, never()).save(any());
        }

        @Test
        void shouldReturnEventWithCurrentPageFromSavedProgress() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.READING, 42, OffsetDateTime.now(), null
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
            when(bookProgressRepository.existsByUserIdAndBookIdAndIsActive(userId, bookId))
                    .thenReturn(false);

            CreateBookProgressEvent event = useCase.execute(command);

            assertThat(event.initPage()).isEqualTo(42);
        }

        @Test
        void shouldReturnCompletedEventWhenEndAtIsProvided() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.READING, 0, OffsetDateTime.now(), OffsetDateTime.now()
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
            when(bookProgressRepository.existsByUserIdAndBookIdAndIsActive(userId, bookId))
                    .thenReturn(false);
            when(book.getPageCount()).thenReturn(300);

            CreateBookProgressEvent event = useCase.execute(command);

            assertThat(event.progress()).isEqualTo(ReadingStatus.COMPLETED.name());
            assertThat(event.initPage()).isEqualTo(300);
        }

        @Test
        void shouldAllowWantToReadStatusWhenBookExists() {
            DefineBookStatusCommand command = new DefineBookStatusCommand(
                    userId, bookId, ReadingStatus.WANT_TO_READ, 0, null, null
            );
            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

            assertThatNoException().isThrownBy(() -> useCase.execute(command));
        }
    }
}
