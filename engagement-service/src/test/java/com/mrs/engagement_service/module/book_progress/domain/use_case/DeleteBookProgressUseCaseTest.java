package com.mrs.engagement_service.module.book_progress.domain.use_case;

import com.mrs.engagement_service.module.book_progress.domain.command.DeleteBookProgressCommand;
import com.mrs.engagement_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.mrs.engagement_service.module.book_progress.domain.port.BookProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteBookProgressUseCaseTest {

    @Mock
    private BookProgressRepository bookProgressRepository;

    @InjectMocks
    private DeleteBookProgressUseCase useCase;

    @Test
    @DisplayName("Should delete progress when it exists")
    void shouldDeleteProgress_WhenExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new DeleteBookProgressCommand(userId, bookId);

        when(bookProgressRepository.existsByUserIdAndBookId(userId, bookId))
                .thenReturn(true);

        // When
        useCase.execute(command);

        // Then
        verify(bookProgressRepository).deleteByUserIdAndBookId(userId, bookId);
    }

    @Test
    @DisplayName("Should throw exception when attempting to delete non-existent progress")
    void shouldThrowException_WhenDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new DeleteBookProgressCommand(userId, bookId);

        when(bookProgressRepository.existsByUserIdAndBookId(userId, bookId))
                .thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookProgressNotFoundException.class);

        verify(bookProgressRepository, never()).deleteByUserIdAndBookId(any(), any());
    }
}