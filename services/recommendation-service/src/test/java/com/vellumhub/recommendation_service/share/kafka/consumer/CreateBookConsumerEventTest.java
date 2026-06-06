package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.CreateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import com.vellumhub.recommendation_service.share.kafka.event.CreateBookEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    private CreateBookConsumerEvent createBookConsumerEvent;
    private SimpleMeterRegistry meterRegistry;

    @Captor
    private ArgumentCaptor<CreateRecommendationCommand> commandCaptor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        createBookConsumerEvent = new CreateBookConsumerEvent(
                createBookFeatureUseCase,
                createRecommendationUseCase,
                new VellumHubMetrics(meterRegistry)
        );
    }

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

    @Test
    @DisplayName("Should count successful consumption and processing duration")
    void shouldCountSuccessfulConsumptionAndDuration() {
        CreateBookEvent event = buildEvent();

        createBookConsumerEvent.listen(event);

        assertThat(meterRegistry.get("vellumhub.kafka.events.consumed")
                .tag("topic", "created-book")
                .tag("event_type", "CreateBookEvent")
                .tag("consumer_group", "recommendation-service")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("vellumhub.kafka.event.processing.duration")
                .tag("topic", "created-book")
                .tag("event_type", "CreateBookEvent")
                .tag("consumer_group", "recommendation-service")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should count failed consumption and processing duration")
    void shouldCountFailedConsumptionAndDuration() {
        CreateBookEvent event = buildEvent();
        doThrow(new RuntimeException("Embedding error")).when(createBookFeatureUseCase).execute(event);

        assertThatThrownBy(() -> createBookConsumerEvent.listen(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding error");

        assertThat(meterRegistry.get("vellumhub.kafka.events.consume.failed")
                .tag("topic", "created-book")
                .tag("event_type", "CreateBookEvent")
                .tag("consumer_group", "recommendation-service")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("vellumhub.kafka.event.processing.duration")
                .tag("topic", "created-book")
                .tag("event_type", "CreateBookEvent")
                .tag("consumer_group", "recommendation-service")
                .tag("result", "failure")
                .timer()
                .count()).isEqualTo(1);
    }
}
