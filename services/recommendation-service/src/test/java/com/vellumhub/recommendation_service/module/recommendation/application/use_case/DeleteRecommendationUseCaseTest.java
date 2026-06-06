package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.recommendation.application.command.DeleteRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
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
class DeleteRecommendationUseCaseTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @InjectMocks
    private DeleteRecommendationUseCase deleteRecommendationUseCase;

    @Test
    @DisplayName("Should delete recommendation by bookId")
    void shouldDeleteRecommendationById() {
        UUID bookId = UUID.randomUUID();
        DeleteRecommendationCommand command = DeleteRecommendationCommand.of(bookId);

        deleteRecommendationUseCase.execute(command);

        verify(recommendationRepository, times(1)).deleteById(bookId);
    }

    @Test
    @DisplayName("Should propagate exception when repository fails to delete")
    void shouldPropagateExceptionWhenRepositoryFails() {
        UUID bookId = UUID.randomUUID();
        DeleteRecommendationCommand command = DeleteRecommendationCommand.of(bookId);
        doThrow(new RuntimeException("DB error")).when(recommendationRepository).deleteById(bookId);

        assertThatThrownBy(() -> deleteRecommendationUseCase.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }
}
