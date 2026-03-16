package com.mrs.recommendation_service.module.book_feature.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.share.event.CreateBookEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @InjectMocks
    private CreateBookFeatureUseCase createBookFeatureUseCase;

    @Test
    @DisplayName("Should save book feature successfully")
    void shouldSaveBookFeatureSuccessfully() {
        // Arrange
        CreateBookEvent createBookEvent = new CreateBookEvent(UUID.randomUUID(), "Book Title", "Author Name",
                2020, "http://cover.jpg", "Lucas", List.of(Genre.FANTASY, Genre.BIOGRAPHY_MEMOIR));
        // Act
        createBookFeatureUseCase.execute(createBookEvent);

    }

}
