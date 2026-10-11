package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    private final OutboxStore store = mock(OutboxStore.class);
    private final KafkaOutboxTransport transport = mock(KafkaOutboxTransport.class);
    private final OutboxDispatcher dispatcher = new OutboxDispatcher(store, transport, new SimpleMeterRegistry());
    private final OutboxRecord record = new OutboxRecord(
            UUID.randomUUID(),UUID.randomUUID(),"reading-progress-events","progress","{}",1);

    @Test void neverMarksPublishedBeforeKafkaAck() {
        var future = new CompletableFuture<SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(record),Optional.empty());
        when(transport.send(record)).thenReturn(future);
        dispatcher.dispatch();
        verify(store,never()).published(any(),any());
        future.complete(null);
        verify(store).published(record.eventId(),record.claimToken());
    }

    @Test void schedulesRetryAfterSendFailure() {
        var future = new CompletableFuture<SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(record),Optional.empty());
        when(transport.send(record)).thenReturn(future);
        dispatcher.dispatch();
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        verify(store).failed(eq(record.eventId()),eq(record.claimToken()),
                contains("broker unavailable"),any());
        verify(store,never()).published(any(),any());
    }
}

