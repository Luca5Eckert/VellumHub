package com.vellumhub.user_service.module.user_preference.infrastructure;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import com.vellumhub.user_service.module.user_preference.application.use_case.PreferenceOutbox;
import com.vellumhub.user_service.share.metrics.VellumHubMetrics;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaPreferenceOutbox implements PreferenceOutbox {
    private static final W3CTraceContextPropagator TRACE = W3CTraceContextPropagator.getInstance();
    private static final String TRACEPARENT = "traceparent";
    private static final TextMapGetter<Map<String, String>> HEADERS = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> headers) {
            return headers.keySet();
        }

        @Override
        public String get(Map<String, String> headers, String key) {
            return headers == null ? null : headers.get(key);
        }
    };

    private final PreferenceOutboxRepository repository;
    private final KafkaTemplate<String, CreateUserPreferenceEvent> kafka;
    private final VellumHubMetrics metrics;
    private final PreferenceAvroPublisher avro;

    KafkaPreferenceOutbox(PreferenceOutboxRepository repository,
                          KafkaTemplate<String, CreateUserPreferenceEvent> kafka, VellumHubMetrics metrics,
                          PreferenceAvroPublisher avro) {
        this.repository = repository;
        this.kafka = kafka;
        this.metrics = metrics;
        this.avro = avro;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID userId, long revision, Long avroRevision, List<String> genres, String about) {
        Map<String, String> headers = new HashMap<>();
        TRACE.inject(Context.current(), headers, Map::put);
        repository.save(new PreferenceOutboxEvent(userId, revision, avroRevision, genres, about,
                headers.get(TRACEPARENT)));
    }

    @Transactional
    public void dispatchNext() {
        var candidate = repository.lockNext();
        if (candidate.isEmpty()) {
            return;
        }

        var event = candidate.get();
        var topic = event.isAvro() ? KafkaTopics.USER_PREFERENCE_EVENTS_AVRO : KafkaTopics.CREATED_USER_PREFERENCE;
        var payload = event.isAvro() ? event.avroPayload() : event.legacyPayload();
        // Old rows and writes without an active span keep the scheduler's context.
        var parent = event.traceparent() == null ? Context.current()
                : TRACE.extract(Context.current(), Map.of(TRACEPARENT, event.traceparent()), HEADERS);
        try (var scope = parent.makeCurrent()) {
            if (payload instanceof CreateUserPreferenceEvent legacy) {
                kafka.send(topic, legacy.userId().toString(), legacy).get(12, TimeUnit.SECONDS);
            } else {
                avro.send((UserPreferenceChanged) payload).get(12, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | TimeoutException | RuntimeException failure) {
            event.retryAfter(Instant.now().plusSeconds(5));
            metrics.recordKafkaPublishFailed(topic, payload);
            return;
        }

        repository.delete(event);
        metrics.recordKafkaPublished(topic, payload);
    }
}
