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
import java.util.concurrent.atomic.AtomicInteger;
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final Duration LEASE = Duration.ofSeconds(120);
    private static final int MAX_IN_FLIGHT = 32;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final OutboxStore store;
    private final KafkaOutboxTransport transport;
    private final Counter published, failed, staleAck;
    public OutboxDispatcher(OutboxStore store, KafkaOutboxTransport transport, MeterRegistry metrics) {
        this.store = store; this.transport = transport;
        published = Counter.builder("vellumhub.outbox.published").register(metrics);
        failed = Counter.builder("vellumhub.outbox.failed").register(metrics);
        staleAck = Counter.builder("vellumhub.outbox.stale_ack").register(metrics);
        Gauge.builder("vellumhub.outbox.pending", store, OutboxStore::pendingCount).register(metrics);
        Gauge.builder("vellumhub.outbox.inflight", inFlight, AtomicInteger::get).register(metrics);
    }
    @Scheduled(fixedDelay=500, initialDelay=2000)
    public void dispatch() {
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
            // Reserve capacity before claiming so a slow broker cannot grow the number
            // of outstanding futures across repeated scheduled invocations.
            if (inFlight.get() >= MAX_IN_FLIGHT) return;
            inFlight.incrementAndGet();
            OutboxRecord record;
            try {
                var candidate = store.claimNext(LEASE);
                if (candidate.isEmpty()) {
                    inFlight.decrementAndGet();
                    return;
                }
                record = candidate.get();
            } catch (RuntimeException ex) {
                inFlight.decrementAndGet();
                throw ex;
            }
            try {
                transport.send(record).whenComplete((ack, error) -> {
                    try {
                        if (error != null) retry(record, error);
                        else {
                            try {
                                if (store.published(record.eventId(), record.claimToken())) {
                                    published.increment();
                                    log.debug("operation=outbox_kafka_ack, eventId={}, topic={}, attempt={}",
                                            record.eventId(), record.topic(), record.attempts());
                                } else {
                                    // Lease was reclaimed: Kafka may have the event, but
                                    // this attempt is no longer allowed to confirm the row.
                                    staleAck.increment();
                                    log.warn("operation=outbox_stale_ack, eventId={}, topic={}",
                                            record.eventId(), record.topic());
                                }
                            } catch (Exception ex) {
                                log.error("Outbox ACK persistence failed; lease will recover. eventId={}", record.eventId(), ex);
                            }
                        }
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (Exception ex) {
                try { retry(record, ex); }
                finally { inFlight.decrementAndGet(); }
            }
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
        log.warn("operation=outbox_publish_failed, eventId={}, topic={}, attempt={}",
                record.eventId(), record.topic(), record.attempts(), failure);
    }
}


