package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.recommendation.application.command.UpdateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.exception.RecommendationDomainException;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateRecommendationUseCaseTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @InjectMocks
    private UpdateRecommendationUseCase updateRecommendationUseCase;

    private Recommendation buildRecommendation(UUID bookId) {
        return Recommendation.builder()
                .bookId(bookId)
                .title("Old Title")
                .description("Old Description")
                .author("Old Author")
                .coverUrl("http://old.url")
                .releaseYear(2010)
                .genres(List.of("Fantasy"))
                .build();
    }

    @Test
    @DisplayName("Should update an existing recommendation successfully")
    void shouldUpdateRecommendationSuccessfully() {
        UUID bookId = UUID.randomUUID();
        Recommendation existing = buildRecommendation(bookId);
        UpdateRecommendationCommand command = UpdateRecommendationCommand.of(
                bookId,
                "New Title",
                "New Description",
                2023,
                "http://new.url",
                "New Author",
                List.of("Science Fiction")
        );

        when(recommendationRepository.findById(bookId)).thenReturn(Optional.of(existing));

        updateRecommendationUseCase.execute(command);

        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(existing.getDescription()).isEqualTo("New Description");
        assertThat(existing.getAuthor()).isEqualTo("New Author");
        assertThat(existing.getCoverUrl()).isEqualTo("http://new.url");
        assertThat(existing.getReleaseYear()).isEqualTo(2023);
        assertThat(existing.getGenres()).containsExactly("Science Fiction");
        verify(recommendationRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw exception when recommendation is not found")
    void shouldThrowExceptionWhenRecommendationNotFound() {
        UUID bookId = UUID.randomUUID();
        UpdateRecommendationCommand command = UpdateRecommendationCommand.of(
                bookId, "Title", "Desc", 2020, "url", "Author", List.of()
        );
        when(recommendationRepository.findById(bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateRecommendationUseCase.execute(command))
                .isInstanceOf(RecommendationDomainException.class)
                .hasMessageContaining("Recommendation not found");

        verify(recommendationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not update fields that are null in the command")
    void shouldNotUpdateNullFields() {
        UUID bookId = UUID.randomUUID();
        Recommendation existing = buildRecommendation(bookId);
        UpdateRecommendationCommand command = UpdateRecommendationCommand.of(
                bookId, null, null, 0, null, null, null
        );

        when(recommendationRepository.findById(bookId)).thenReturn(Optional.of(existing));

        updateRecommendationUseCase.execute(command);

        assertThat(existing.getTitle()).isEqualTo("Old Title");
        assertThat(existing.getDescription()).isEqualTo("Old Description");
        assertThat(existing.getAuthor()).isEqualTo("Old Author");
        assertThat(existing.getCoverUrl()).isEqualTo("http://old.url");
        assertThat(existing.getReleaseYear()).isEqualTo(2010);
        assertThat(existing.getGenres()).containsExactly("Fantasy");
        verify(recommendationRepository).save(existing);
    }
}
