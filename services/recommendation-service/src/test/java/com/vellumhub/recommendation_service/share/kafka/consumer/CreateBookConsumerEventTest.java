package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.CreateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.kafka.event.CreateBookEvent;
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
class CreateBookConsumerEventTest {

    @Mock
    private CreateBookFeatureUseCase createBookFeatureUseCase;

    @Mock
    private CreateRecommendationUseCase createRecommendationUseCase;

    @InjectMocks
    private CreateBookConsumerEvent createBookConsumerEvent;

    @Captor
    private ArgumentCaptor<CreateRecommendationCommand> commandCaptor;

    private CreateBookEvent buildEvent() {
        return new CreateBookEvent(
                UUID.randomUUID(),
                "Domain-Driven Design",
                "A guide to DDD",
                2003,
                "https://cover.url",
                "Eric Evans",
                List.of("Software", "Architecture")
        );
    }

    @Test
    @DisplayName("Should invoke both use cases when event is received")
    void shouldInvokeBothUseCasesOnEvent() {
        CreateBookEvent event = buildEvent();

        createBookConsumerEvent.listen(event);

        verify(createBookFeatureUseCase, times(1)).execute(event);
        verify(createRecommendationUseCase, times(1)).execute(any(CreateRecommendationCommand.class));
    }

    @Test
    @DisplayName("Should pass correct data to CreateRecommendationUseCase")
    void shouldPassCorrectDataToCreateRecommendationUseCase() {
        CreateBookEvent event = buildEvent();

        createBookConsumerEvent.listen(event);

        verify(createRecommendationUseCase).execute(commandCaptor.capture());
        CreateRecommendationCommand command = commandCaptor.getValue();

        assertThat(command.bookId()).isEqualTo(event.bookId());
        assertThat(command.title()).isEqualTo(event.title());
        assertThat(command.description()).isEqualTo(event.description());
        assertThat(command.releaseYear()).isEqualTo(event.releaseYear());
        assertThat(command.coverUrl()).isEqualTo(event.coverUrl());
        assertThat(command.author()).isEqualTo(event.author());
        assertThat(command.genres()).isEqualTo(event.genres());
    }

    @Test
    @DisplayName("Should propagate exception when CreateBookFeatureUseCase fails")
    void shouldPropagateExceptionWhenBookFeatureUseCaseFails() {
        CreateBookEvent event = buildEvent();
        doThrow(new RuntimeException("Embedding error")).when(createBookFeatureUseCase).execute(event);

        assertThatThrownBy(() -> createBookConsumerEvent.listen(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding error");

        verify(createRecommendationUseCase, never()).execute(any());
    }
}
