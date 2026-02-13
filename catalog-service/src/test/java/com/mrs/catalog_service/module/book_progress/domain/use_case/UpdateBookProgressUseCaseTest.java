package com.mrs.catalog_service.module.book_progress.domain.use_case;

import com.mrs.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.mrs.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.mrs.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.mrs.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.mrs.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        var bookProgress = new BookProgress(bookId, userId);
        bookProgress.setReadingStatus(ReadingStatus.READING);
        bookProgress.setCurrentPage(50);

        when(bookProgressRepository.findByUserIdAndBookId(bookId, userId))
                .thenReturn(Optional.of(bookProgress));

        when(bookProgressRepository.save(bookProgress)).thenReturn(bookProgress);

        // When
        BookProgress result = useCase.execute(command);

        // Then
        assertThat(result.getCurrentPage()).isEqualTo(newPage);
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

        var bookProgress = new BookProgress(bookId, userId);
        bookProgress.setReadingStatus(ReadingStatus.WANT_TO_READ); // Not READING

        when(bookProgressRepository.findByUserIdAndBookId(bookId, userId))
                .thenReturn(Optional.of(bookProgress));

        // When/Then
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookIsNotBeingReadException.class);
    }
}