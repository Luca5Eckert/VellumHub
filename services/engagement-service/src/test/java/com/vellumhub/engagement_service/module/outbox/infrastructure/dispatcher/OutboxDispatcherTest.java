package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    private final OutboxStore store=mock(OutboxStore.class);
    private final KafkaOutboxTransport kafka=mock(KafkaOutboxTransport.class);
    private final OutboxDispatcher dispatcher=new OutboxDispatcher(store,kafka,new SimpleMeterRegistry());
    private OutboxRecord claim() {
        return new OutboxRecord(UUID.randomUUID(),UUID.randomUUID(),"RATING",
                "RatingCreatedEvent","engagement.ratings.v1","user","{}",1);
    }
    @Test void marksPublishedOnlyAfterBrokerAcknowledgement() {
        var claim=claim();
        var ack=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(claim),Optional.empty());
        when(kafka.send(claim)).thenReturn(ack);
        dispatcher.dispatch();
        verify(store,never()).published(any(),any());
        ack.complete(null);
        verify(store).published(claim.eventId(),claim.claimToken());
    }
    @Test void brokerFailureSchedulesRetryWithoutMarkingPublished() {
        var claim=claim();
        var fail=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(claim),Optional.empty());
        when(kafka.send(claim)).thenReturn(fail);
        dispatcher.dispatch();
        fail.completeExceptionally(new IllegalStateException("broker offline"));
        verify(store).failed(eq(claim.eventId()),eq(claim.claimToken()),
                contains("broker offline"),any());
        verify(store,never()).published(any(),any());
    }
}

