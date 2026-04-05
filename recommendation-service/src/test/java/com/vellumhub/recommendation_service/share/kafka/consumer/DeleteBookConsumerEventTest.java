package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.use_case.DeleteBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.DeleteRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.DeleteRecommendationUseCase;
import com.vellumhub.recommendation_service.share.kafka.event.DeleteBookEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteBookConsumerEventTest {

    @Mock
    private DeleteBookFeatureUseCase deleteBookFeatureUseCase;

    @Mock
    private DeleteRecommendationUseCase deleteRecommendationUseCase;

    @InjectMocks
    private DeleteBookConsumerEvent deleteBookConsumerEvent;

    @Captor
    private ArgumentCaptor<DeleteRecommendationCommand> commandCaptor;

    @Test
    @DisplayName("Should invoke both use cases when book deletion event is received")
    void shouldInvokeBothUseCasesOnEvent() {
        UUID bookId = UUID.randomUUID();
        DeleteBookEvent event = new DeleteBookEvent(bookId);

        deleteBookConsumerEvent.listen(event);

        verify(deleteBookFeatureUseCase, times(1)).execute(bookId);
        verify(deleteRecommendationUseCase, times(1)).execute(any(DeleteRecommendationCommand.class));
    }

    @Test
    @DisplayName("Should pass correct bookId to DeleteRecommendationUseCase")
    void shouldPassCorrectBookIdToDeleteRecommendationUseCase() {
        UUID bookId = UUID.randomUUID();
        DeleteBookEvent event = new DeleteBookEvent(bookId);

        deleteBookConsumerEvent.listen(event);

        verify(deleteRecommendationUseCase).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().bookId()).isEqualTo(bookId);
    }

    @Test
    @DisplayName("Should pass correct bookId to DeleteBookFeatureUseCase")
    void shouldPassCorrectBookIdToDeleteBookFeatureUseCase() {
        UUID bookId = UUID.randomUUID();
        DeleteBookEvent event = new DeleteBookEvent(bookId);

        deleteBookConsumerEvent.listen(event);

        verify(deleteBookFeatureUseCase).execute(bookId);
    }

    @Test
    @DisplayName("Should propagate exception when DeleteBookFeatureUseCase fails")
    void shouldPropagateExceptionWhenBookFeatureUseCaseFails() {
        UUID bookId = UUID.randomUUID();
        DeleteBookEvent event = new DeleteBookEvent(bookId);
        doThrow(new RuntimeException("Delete error")).when(deleteBookFeatureUseCase).execute(bookId);

        assertThatThrownBy(() -> deleteBookConsumerEvent.listen(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Delete error");

        verify(deleteRecommendationUseCase, never()).execute(any());
    }
}
