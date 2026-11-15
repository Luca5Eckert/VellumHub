package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    private final OutboxStore store = mock(OutboxStore.class);
    private final KafkaOutboxTransport transport = mock(KafkaOutboxTransport.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final OutboxDispatcher dispatcher = new OutboxDispatcher(store, transport, metrics);
    private final OutboxRecord record = new OutboxRecord(
            UUID.randomUUID(),UUID.randomUUID(),"reading-progress-events","progress","{}",1);

    @BeforeEach void resetStubs() {
        when(store.published(any(), any())).thenReturn(true);
    }

    @Test void neverMarksPublishedBeforeKafkaAck() {
        var future = new CompletableFuture<SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(record),Optional.empty());
        when(transport.send(record)).thenReturn(future);
        dispatcher.dispatch();
        verify(store,never()).published(any(),any());
        future.complete(null);
        verify(store).published(record.eventId(),record.claimToken());
    }

    @Test void staleAckMustNotCountAsPublished() {
        var future = new CompletableFuture<SendResult<String,Object>>();
        when(store.claimNext(any())).thenReturn(Optional.of(record), Optional.empty());
        when(transport.send(record)).thenReturn(future);
        when(store.published(record.eventId(), record.claimToken())).thenReturn(false);
        dispatcher.dispatch();
        future.complete(null);
        assertThat(metrics.get("vellumhub.outbox.published").counter().count()).isZero();
        assertThat(metrics.get("vellumhub.outbox.stale_ack").counter().count()).isEqualTo(1);
        assertThat(metrics.get("vellumhub.outbox.inflight").gauge().value()).isZero();
    }

    @Test void pendingFuturesAreBoundedAcrossScheduledRuns() {
        List<CompletableFuture<SendResult<String,Object>>> pending = new ArrayList<>();
        when(store.claimNext(any())).thenAnswer(invocation -> Optional.of(
                new OutboxRecord(UUID.randomUUID(), UUID.randomUUID(),
                        "reading-progress-events", "progress", "{}", 1)));
        when(transport.send(any())).thenAnswer(invocation -> {
            var future = new CompletableFuture<SendResult<String,Object>>();
            pending.add(future);
            return future;
        });
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(transport, times(32)).send(any());
        assertThat(metrics.get("vellumhub.outbox.inflight").gauge().value()).isEqualTo(32);
        pending.forEach(f -> f.complete(null));
        assertThat(metrics.get("vellumhub.outbox.inflight").gauge().value()).isZero();
        dispatcher.dispatch();
        verify(transport, times(64)).send(any());
        pending.forEach(f -> f.complete(null));
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

