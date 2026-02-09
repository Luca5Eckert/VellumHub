package com.mrs.engagement_service.module.book_progress.domain.use_case;

import com.mrs.engagement_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.mrs.engagement_service.module.book_progress.domain.model.ReadingStatus;
import com.mrs.engagement_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefineBookStatusUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @InjectMocks
    private DefineBookStatusUseCase useCase;

    @Test
    @DisplayName("Should create new progress when it does not exist")
    void shouldCreateNewProgress_WhenNoneExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new DefineBookStatusCommand(userId, bookId, ReadingStatus.WANT_TO_READ, 0);

        when(bookProgressRepository.findByUserIdAndBookId(userId, bookId))
                .thenReturn(Optional.empty());
        when(bookProgressRepository.save(any(BookProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookProgress result = useCase.execute(command);

        // Then
        ArgumentCaptor<BookProgress> captor = ArgumentCaptor.forClass(BookProgress.class);
        verify(bookProgressRepository).save(captor.capture());

        BookProgress captured = captor.getValue();
        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getBookId()).isEqualTo(bookId);
        assertThat(captured.getReadingStatus()).isEqualTo(ReadingStatus.WANT_TO_READ);
        assertThat(captured.getCurrentPage()).isZero();
    }

    @Test
    @DisplayName("Should update existing progress and set page when valid")
    void shouldUpdateExistingProgress_WhenExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var existingProgress = new BookProgress(bookId, userId);
        existingProgress.setReadingStatus(ReadingStatus.WANT_TO_READ);

        // Command creates a change to READING and page 50
        var command = new DefineBookStatusCommand(userId, bookId, ReadingStatus.READING, 50);

        when(bookProgressRepository.findByUserIdAndBookId(userId, bookId))
                .thenReturn(Optional.of(existingProgress));
        when(bookProgressRepository.save(any(BookProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookProgress result = useCase.execute(command);

        // Then
        assertThat(result.getReadingStatus()).isEqualTo(ReadingStatus.READING);
        assertThat(result.getCurrentPage()).isEqualTo(50);
        verify(bookProgressRepository).save(existingProgress);
    }

    @Test
    @DisplayName("Should ignore page update if page number is negative")
    void shouldIgnorePageUpdate_WhenPageIsNegative() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var existingProgress = new BookProgress(bookId, userId);
        existingProgress.setCurrentPage(100); // Originally at page 100

        // Command sends -1 as page
        var command = new DefineBookStatusCommand(userId, bookId, ReadingStatus.READING, -1);

        when(bookProgressRepository.findByUserIdAndBookId(userId, bookId))
                .thenReturn(Optional.of(existingProgress));
        when(bookProgressRepository.save(any(BookProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        BookProgress result = useCase.execute(command);

        // Then
        assertThat(result.getCurrentPage()).isEqualTo(100); // Should remain 100
        assertThat(result.getReadingStatus()).isEqualTo(ReadingStatus.READING); // Status still updates
    }
}