package com.vellumhub.engagement_service.module.rating.application.event;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RatingEventRecorderTest {
    private final OutboxWriter outbox=mock(OutboxWriter.class);
    private final RatingEventRecorder recorder=new RatingEventRecorder(outbox);
    private Rating rating() {
        return Rating.builder().id(4L).userId(UUID.randomUUID()).bookId(UUID.randomUUID())
                .stars(5).version(2).build();
    }
    @Test void createdRatingIsImmutablePastTenseFactWithStableKeyAndRevision() {
        var rating=rating();
        recorder.created(rating);
        var captor=ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).append(captor.capture());
        var row=captor.getValue();
        assertThat(row.topic()).isEqualTo(KafkaTopics.ENGAGEMENT_RATINGS);
        assertThat(row.aggregateId()).isEqualTo("4");
        assertThat(row.revision()).isEqualTo(2);
        assertThat(row.key()).isEqualTo(rating.getUserId().toString());
        var fact=(RatingCreatedEvent)row.payload();
        assertThat(fact.eventId()).isEqualTo(row.eventId());
        assertThat(fact.occurredAt()).isNotNull();
    }
    @Test void deletionCarriesPreRemovalScoreAndNextRevision() {
        var rating=rating();
        recorder.deleted(rating);
        var cap=ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).append(cap.capture());
        var message=cap.getValue();
        assertThat(message.revision()).isEqualTo(3);
        var fact=(RatingDeletedEvent)message.payload();
        assertThat(fact.oldStars()).isEqualTo(5);
        assertThat(fact.revision()).isEqualTo(3);
    }
    @Test void noOpUpdateDoesNotEmit() {
        var rating=rating();
        recorder.updated(new RatingUpdateResult(rating,5,false));
        verifyNoInteractions(outbox);
    }
    @Test void updateCarriesPreviousAndNewScore() {
        var rating=rating();
        recorder.updated(new RatingUpdateResult(rating,2,true));
        var cap=ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).append(cap.capture());
        var event=(RatingUpdatedEvent)cap.getValue().payload();
        assertThat(event.oldStars()).isEqualTo(2);
        assertThat(event.newStars()).isEqualTo(5);
        assertThat(event.reviewChanged()).isTrue();
    }
}

