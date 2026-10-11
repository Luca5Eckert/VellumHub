package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final Duration LEASE = Duration.ofSeconds(120);
    private final OutboxStore store;
    private final KafkaOutboxTransport transport;
    private final Counter published, failed;
    public OutboxDispatcher(OutboxStore store, KafkaOutboxTransport transport, MeterRegistry metrics) {
        this.store = store; this.transport = transport;
        published = Counter.builder("vellumhub.outbox.published").register(metrics);
        failed = Counter.builder("vellumhub.outbox.failed").register(metrics);
        Gauge.builder("vellumhub.outbox.pending", store, OutboxStore::pendingCount).register(metrics);
    }
    @Scheduled(fixedDelay=500, initialDelay=2000)
    public void dispatch() {
        for (int i = 0; i < 32; i++) {
            var candidate = store.claimNext(LEASE);
            if (candidate.isEmpty()) return;
            OutboxRecord record = candidate.get();
            try {
                transport.send(record).whenComplete((ack, error) -> {
                    if (error != null) retry(record, error);
                    else {
                        try {
                            store.published(record.eventId(), record.claimToken());
                            published.increment();
                        } catch (Exception ex) {
                            log.error("Outbox ACK persistence failed; lease will recover. eventId={}", record.eventId(), ex);
                        }
                    }
                });
            } catch (Exception ex) { retry(record, ex); }
        }
    }
    private void retry(OutboxRecord record, Throwable failure) {
        failed.increment();
        long seconds = Math.min(300L, 1L << Math.min(8, record.attempts()));
        try {
            store.failed(record.eventId(), record.claimToken(),
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                    Duration.ofSeconds(seconds));
        } catch (Exception ex) {
            log.error("Outbox retry persistence failed; lease will recover. eventId={}", record.eventId(), ex);
        }
        log.warn("Outbox publish failed. eventId={}, attempt={}", record.eventId(), record.attempts(), failure);
    }
}


