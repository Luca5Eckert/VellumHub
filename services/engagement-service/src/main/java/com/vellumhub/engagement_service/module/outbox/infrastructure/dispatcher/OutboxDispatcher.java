package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxRecord;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxStore;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Asynchronous at-least-once relay; marks published ONLY following broker ACK. */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.outbox.enabled",havingValue="true")
public class OutboxDispatcher {
    private static final Duration LEASE=Duration.ofSeconds(120);
    private final OutboxStore store;
    private final KafkaOutboxTransport transport;
    private final Counter published,failed;
    public OutboxDispatcher(OutboxStore store,KafkaOutboxTransport transport,MeterRegistry metrics) {
        this.store=store;this.transport=transport;
        published=Counter.builder("vellumhub.outbox.published").tag("service","engagement").register(metrics);
        failed=Counter.builder("vellumhub.outbox.failed").tag("service","engagement").register(metrics);
        Gauge.builder("vellumhub.outbox.pending",store,OutboxStore::pendingCount)
                .tag("service","engagement").register(metrics);
    }
    @Scheduled(fixedDelayString="${app.outbox.poll-ms:500}",initialDelayString="${app.outbox.initial-delay-ms:2000}")
    public void dispatch() {
        for(int i=0;i<32;i++) {
            var candidate=store.claimNext(LEASE);
            if(candidate.isEmpty()) return;
            var event=candidate.get();
            try {
                transport.send(event).whenComplete((ack,error)-> {
                    if(error!=null) retry(event,error);
                    else try {
                        store.published(event.eventId(),event.claimToken());
                        published.increment();
                        log.debug("operation=outbox_published eventId={} aggregateType={} topic={} attempt={}",
                                event.eventId(),event.aggregateType(),event.topic(),event.attempts());
                    }catch(Exception exception) {
                        log.error("operation=outbox_ack_persist_failed eventId={} topic={}",
                                event.eventId(),event.topic(),exception);
                    }
                });
            } catch(Exception error) {retry(event,error);}
        }
    }
    private void retry(OutboxRecord event,Throwable error) {
        failed.increment();
        long seconds=Math.min(300L,1L<<Math.min(8,event.attempts()));
        try {
            store.failed(event.eventId(),event.claimToken(),
                    error.getClass().getSimpleName()+": "+error.getMessage(),
                    Duration.ofSeconds(seconds));
        }catch(Exception exception){
            log.error("operation=outbox_retry_persist_failed eventId={}",event.eventId(),exception);
        }
        log.warn("operation=outbox_delivery_failed eventId={} type={} topic={} attempt={}",
                event.eventId(),event.eventType(),event.topic(),event.attempts(),error);
    }
}

