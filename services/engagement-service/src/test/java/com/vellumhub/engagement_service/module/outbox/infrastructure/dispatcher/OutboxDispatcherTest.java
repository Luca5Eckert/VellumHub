package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    private final OutboxStore store=mock(OutboxStore.class);
    private final KafkaOutboxTransport kafka=mock(KafkaOutboxTransport.class);
    private final SimpleMeterRegistry meterRegistry=new SimpleMeterRegistry();
    private final OutboxDispatcher dispatcher=new OutboxDispatcher(store,kafka,meterRegistry);
    private OutboxRecord claim() {
        return new OutboxRecord(UUID.randomUUID(),UUID.randomUUID(),"RATING",
                "RatingCreatedEvent","engagement.ratings.v1","user","{}",1);
    }
    @Test void marksPublishedOnlyAfterBrokerAcknowledgement() {
        var claim=claim();
        var ack=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(claim),Optional.empty());
        when(kafka.send(claim)).thenReturn(ack);
        when(store.published(claim.eventId(),claim.claimToken())).thenReturn(true);
        dispatcher.dispatch();
        verify(store,never()).published(any(),any());
        ack.complete(null);
        verify(store).published(claim.eventId(),claim.claimToken());
        assertThat(meterRegistry.get("vellumhub.outbox.published").counter().count()).isEqualTo(1.0);
    }
    @Test void staleLeaseAcknowledgementDoesNotInflatePublishedMetric() {
        var claim=claim();
        var ack=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(claim),Optional.empty());
        when(kafka.send(claim)).thenReturn(ack);
        when(store.published(claim.eventId(),claim.claimToken())).thenReturn(false);
        dispatcher.dispatch();
        ack.complete(null);
        assertThat(meterRegistry.get("vellumhub.outbox.published").counter().count()).isZero();
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

