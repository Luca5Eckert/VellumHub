package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateRecommendationUseCaseTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @InjectMocks
    private CreateRecommendationUseCase createRecommendationUseCase;

    @Captor
    private ArgumentCaptor<Recommendation> recommendationCaptor;

    @Test
    @DisplayName("Should create and save a recommendation with all fields")
    void shouldCreateAndSaveRecommendation() {
        UUID bookId = UUID.randomUUID();
        CreateRecommendationCommand command = CreateRecommendationCommand.of(
                bookId,
                "Clean Code",
                "A handbook of agile software craftsmanship",
                2008,
                "https://example.com/cover.jpg",
                "Robert C. Martin",
                List.of("Software Engineering", "Best Practices")
        );

        createRecommendationUseCase.execute(command);

        verify(recommendationRepository).save(recommendationCaptor.capture());
        Recommendation saved = recommendationCaptor.getValue();

        assertThat(saved.getBookId()).isEqualTo(bookId);
        assertThat(saved.getTitle()).isEqualTo("Clean Code");
        assertThat(saved.getDescription()).isEqualTo("A handbook of agile software craftsmanship");
        assertThat(saved.getReleaseYear()).isEqualTo(2008);
        assertThat(saved.getCoverUrl()).isEqualTo("https://example.com/cover.jpg");
        assertThat(saved.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(saved.getGenres()).containsExactly("Software Engineering", "Best Practices");
    }

    @Test
    @DisplayName("Should save recommendation exactly once")
    void shouldSaveExactlyOnce() {
        UUID bookId = UUID.randomUUID();
        CreateRecommendationCommand command = CreateRecommendationCommand.of(
                bookId, "Title", "Desc", 2020, "url", "Author", List.of()
        );

        createRecommendationUseCase.execute(command);

        verify(recommendationRepository, times(1)).save(any(Recommendation.class));
    }

    @Test
    @DisplayName("Should propagate exception when repository fails to save")
    void shouldPropagateExceptionWhenRepositoryFails() {
        UUID bookId = UUID.randomUUID();
        CreateRecommendationCommand command = CreateRecommendationCommand.of(
                bookId, "Title", "Desc", 2020, "url", "Author", List.of()
        );
        doThrow(new RuntimeException("DB error")).when(recommendationRepository).save(any());

        assertThatThrownBy(() -> createRecommendationUseCase.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }
}
