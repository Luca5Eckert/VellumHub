package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.UpdateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.UpdateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.UpdateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.kafka.event.UpdateBookEvent;
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
class UpdateBookConsumerEventTest {

    @Mock
    private UpdateBookFeatureUseCase updateBookFeatureUseCase;

    @Mock
    private UpdateRecommendationUseCase updateRecommendationUseCase;

    @InjectMocks
    private UpdateBookConsumerEvent updateBookConsumerEvent;

    @Captor
    private ArgumentCaptor<UpdateBookFeatureCommand> bookFeatureCommandCaptor;

    @Captor
    private ArgumentCaptor<UpdateRecommendationCommand> recommendationCommandCaptor;

    private UpdateBookEvent buildEvent() {
        return new UpdateBookEvent(
                UUID.randomUUID(),
                "Updated Title",
                "Updated Description",
                2024,
                "https://updated.cover.url",
                "Updated Author",
                List.of("Updated Genre")
        );
    }

    @Test
    @DisplayName("Should invoke both use cases when book update event is received")
    void shouldInvokeBothUseCasesOnEvent() {
        UpdateBookEvent event = buildEvent();

        updateBookConsumerEvent.execute(event);

        verify(updateBookFeatureUseCase, times(1)).execute(any(UpdateBookFeatureCommand.class));
        verify(updateRecommendationUseCase, times(1)).execute(any(UpdateRecommendationCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to UpdateBookFeatureUseCase")
    void shouldPassCorrectDataToUpdateBookFeatureUseCase() {
        UpdateBookEvent event = buildEvent();

        updateBookConsumerEvent.execute(event);

        verify(updateBookFeatureUseCase).execute(bookFeatureCommandCaptor.capture());
        UpdateBookFeatureCommand command = bookFeatureCommandCaptor.getValue();

        assertThat(command.bookId()).isEqualTo(event.bookId());
        assertThat(command.title()).isEqualTo(event.title());
        assertThat(command.author()).isEqualTo(event.author());
        assertThat(command.description()).isEqualTo(event.description());
        assertThat(command.genres()).isEqualTo(event.genres());
    }

    @Test
    @DisplayName("Should pass correct data to UpdateRecommendationUseCase")
    void shouldPassCorrectDataToUpdateRecommendationUseCase() {
        UpdateBookEvent event = buildEvent();

        updateBookConsumerEvent.execute(event);

        verify(updateRecommendationUseCase).execute(recommendationCommandCaptor.capture());
        UpdateRecommendationCommand command = recommendationCommandCaptor.getValue();

        assertThat(command.bookId()).isEqualTo(event.bookId());
        assertThat(command.title()).isEqualTo(event.title());
        assertThat(command.description()).isEqualTo(event.description());
        assertThat(command.releaseYear()).isEqualTo(event.releaseYear());
        assertThat(command.coverUrl()).isEqualTo(event.coverUrl());
        assertThat(command.author()).isEqualTo(event.author());
        assertThat(command.genres()).isEqualTo(event.genres());
    }

    @Test
    @DisplayName("Should propagate exception when UpdateBookFeatureUseCase fails")
    void shouldPropagateExceptionWhenBookFeatureUseCaseFails() {
        UpdateBookEvent event = buildEvent();
        doThrow(new RuntimeException("Update error")).when(updateBookFeatureUseCase).execute(any());

        assertThatThrownBy(() -> updateBookConsumerEvent.execute(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Update error");

        verify(updateRecommendationUseCase, never()).execute(any());
    }
}
