package com.vellumhub.recommendation_service.module.recommendation.presentation.controller;

import com.vellumhub.recommendation_service.module.recommendation.application.command.GetRecommendationsCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.GetRecommendationsUseCase;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import com.vellumhub.recommendation_service.module.recommendation.presentation.mapper.RecommendationMapper;
import com.vellumhub.recommendation_service.share.provider.UserAuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private UserAuthenticationProvider userAuthenticationProvider;

    @Mock
    private RecommendationMapper mapper;

    @Mock
    private GetRecommendationsUseCase getRecommendationsUseCase;

    @InjectMocks
    private RecommendationController controller;

    @Captor
    private ArgumentCaptor<GetRecommendationsCommand> commandCaptor;

    private Recommendation buildRecommendation(UUID bookId, String title) {
        return Recommendation.builder()
                .bookId(bookId)
                .title(title)
                .description("Desc")
                .author("Author")
                .coverUrl("http://cover.url")
                .releaseYear(2020)
                .genres(List.of("Fiction"))
                .build();
    }

    @Test
    @DisplayName("Should return 200 with list of recommendations for authenticated user")
    void shouldReturnRecommendationsForUser() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Recommendation rec = buildRecommendation(bookId, "Book A");
        RecommendationResponse recResponse = new RecommendationResponse(bookId, "Book A", "Desc", 2020, "http://cover.url", "Author", List.of("Fiction"));

        when(userAuthenticationProvider.getUserId()).thenReturn(userId);
        when(getRecommendationsUseCase.execute(any(GetRecommendationsCommand.class))).thenReturn(List.of(rec));
        when(mapper.toResponse(rec)).thenReturn(recResponse);

        ResponseEntity<List<RecommendationResponse>> response = controller.getRecommendations(10, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).title()).isEqualTo("Book A");
    }

    @Test
    @DisplayName("Should pass correct command with userId, limit and offset")
    void shouldBuildCommandWithCorrectValues() {
        UUID userId = UUID.randomUUID();
        when(userAuthenticationProvider.getUserId()).thenReturn(userId);
        when(getRecommendationsUseCase.execute(commandCaptor.capture())).thenReturn(List.of());

        controller.getRecommendations(5, 10);

        GetRecommendationsCommand captured = commandCaptor.getValue();
        assertThat(captured.userId()).isEqualTo(userId);
        assertThat(captured.limit()).isEqualTo(5);
        assertThat(captured.offset()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should return empty list when no recommendations found")
    void shouldReturnEmptyListWhenNoRecommendations() {
        UUID userId = UUID.randomUUID();
        when(userAuthenticationProvider.getUserId()).thenReturn(userId);
        when(getRecommendationsUseCase.execute(any())).thenReturn(List.of());

        ResponseEntity<List<RecommendationResponse>> response = controller.getRecommendations(10, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(mapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("Should map each recommendation to response")
    void shouldMapEachRecommendationToResponse() {
        UUID userId = UUID.randomUUID();
        UUID bookId1 = UUID.randomUUID();
        UUID bookId2 = UUID.randomUUID();
        Recommendation rec1 = buildRecommendation(bookId1, "Book 1");
        Recommendation rec2 = buildRecommendation(bookId2, "Book 2");

        when(userAuthenticationProvider.getUserId()).thenReturn(userId);
        when(getRecommendationsUseCase.execute(any())).thenReturn(List.of(rec1, rec2));
        when(mapper.toResponse(rec1)).thenReturn(new RecommendationResponse(bookId1, "Book 1", "Desc", 2020, "url", "Author", List.of()));
        when(mapper.toResponse(rec2)).thenReturn(new RecommendationResponse(bookId2, "Book 2", "Desc", 2021, "url", "Author", List.of()));

        ResponseEntity<List<RecommendationResponse>> response = controller.getRecommendations(10, 0);

        assertThat(response.getBody()).hasSize(2);
        verify(mapper, times(2)).toResponse(any());
    }
}
