package com.mrs.recommendation_service.module.book_feature.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMediaFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @InjectMocks
    private UpdateMediaFeatureUseCase updateMediaFeatureUseCase;

    @Test
    @DisplayName("Should update book feature successfully when it exists")
    void shouldUpdateBookFeatureSuccessfully() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        BookFeature existingFeature = new BookFeature(bookId, new float[Genre.total()]);
        UpdateBookFeatureCommand command = new UpdateBookFeatureCommand(bookId, List.of(Genre.FANTASY, Genre.HORROR));

        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(existingFeature));

        // Act
        updateMediaFeatureUseCase.execute(command);

        // Assert
        verify(bookFeatureRepository, times(1)).findById(bookId);
        verify(bookFeatureRepository, times(1)).save(existingFeature);
    }

    @Test
    @DisplayName("Should throw BookFeatureNotFoundException when feature does not exist")
    void shouldThrowExceptionWhenBookFeatureNotFound() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        UpdateBookFeatureCommand command = new UpdateBookFeatureCommand(bookId, List.of(Genre.ROMANCE));

        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> updateMediaFeatureUseCase.execute(command))
                .isInstanceOf(BookFeatureNotFoundException.class);

        verify(bookFeatureRepository, never()).save(any());
    }
}
