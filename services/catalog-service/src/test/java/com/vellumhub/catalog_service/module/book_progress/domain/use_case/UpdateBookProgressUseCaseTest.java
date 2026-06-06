package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateBookProgressUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @InjectMocks
    private UpdateBookProgressUseCase useCase;

    @Test
    @DisplayName("Should update progress when book exists and is in READING status")
    void shouldUpdateProgress_WhenStatusIsReading() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        int newPage = 120;

        var command = new UpdateBookProgressCommand(bookId, userId, newPage);

        // CORREÇÃO 1: Adicionado pageCount para evitar que o update() marque como COMPLETED (0)
        Book book = Book.builder().id(bookId).pageCount(300).build();

        // CORREÇÃO 2: Utilização do factory method "create" no lugar do construtor/setters
        var bookProgress = BookProgress.create(
                userId, book, 50, ReadingStatus.READING, OffsetDateTime.now(), null
        );

        when(bookProgressRepository.findByUserIdAndBookId(bookId, userId))
                .thenReturn(Optional.of(bookProgress));

        when(bookProgressRepository.save(any(BookProgress.class))).thenReturn(bookProgress);

        // When
        var result = useCase.execute(command);

        // Then
        assertThat(result.newPage()).isEqualTo(newPage);
        verify(bookProgressRepository).save(bookProgress);
    }

    @Test
    @DisplayName("Should throw exception when book progress is not found")
    void shouldThrowException_WhenProgressNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new UpdateBookProgressCommand(bookId, userId, 10);

        when(bookProgressRepository.findByUserIdAndBookId(bookId, userId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookProgressNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw exception when book is not currently being read")
    void shouldThrowException_WhenStatusIsNotReading() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new UpdateBookProgressCommand(bookId, userId, 10);

        Book book = Book.builder().id(bookId).pageCount(300).build();

        // CORREÇÃO 3: Criando a entidade já com o status correto de WANT_TO_READ
        var bookProgress = BookProgress.create(
                userId, book, 0, ReadingStatus.WANT_TO_READ, null, null
        );

        when(bookProgressRepository.findByUserIdAndBookId(bookId, userId))
                .thenReturn(Optional.of(bookProgress));

        // When/Then
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookIsNotBeingReadException.class);
    }
}