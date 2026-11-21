package com.vellumhub.user_service.module.user_preference.infrastructure;

import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import com.vellumhub.user_service.module.user_preference.application.use_case.PreferenceOutbox;
import com.vellumhub.user_service.share.metrics.VellumHubMetrics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaPreferenceOutbox implements PreferenceOutbox {
    private final PreferenceOutboxRepository repository;
    private final KafkaTemplate<String, CreateUserPreferenceEvent> kafka;
    private final VellumHubMetrics metrics;

    KafkaPreferenceOutbox(PreferenceOutboxRepository repository,
                          KafkaTemplate<String, CreateUserPreferenceEvent> kafka, VellumHubMetrics metrics) {
        this.repository = repository;
        this.kafka = kafka;
        this.metrics = metrics;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID userId, long revision, List<String> genres, String about) {
        repository.save(new PreferenceOutboxEvent(userId, revision, genres, about));
    }

    @Transactional
    public void dispatchNext() {
        var candidate = repository.lockNext();
        if (candidate.isEmpty()) {
            return;
        }

        var event = candidate.get();
        var payload = event.legacyPayload();
        var topic = KafkaTopics.CREATED_USER_PREFERENCE;
        try {
            kafka.send(topic, payload.userId().toString(), payload).get(12, TimeUnit.SECONDS);
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
