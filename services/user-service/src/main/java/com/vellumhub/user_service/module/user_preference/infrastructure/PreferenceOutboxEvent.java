package com.vellumhub.user_service.module.user_preference.infrastructure;

import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preference_outbox")
class PreferenceOutboxEvent {
    @Id
    private UUID eventId;
    private UUID userId;
    private long revision;
    private Long avroRevision;
    private Instant occurredAt;
    private Instant nextAttemptAt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] genres;
    private String about;
    private String traceparent;

    protected PreferenceOutboxEvent() {}

    PreferenceOutboxEvent(UUID userId, long revision, Long avroRevision, List<String> genres, String about, String traceparent) {
        eventId = UUID.randomUUID();
        occurredAt = Instant.now();
        nextAttemptAt = occurredAt;
        this.userId = userId;
        this.revision = revision;
        this.avroRevision = avroRevision;
        this.genres = genres.toArray(String[]::new);
        this.about = about;
        this.traceparent = traceparent;
    }

    CreateUserPreferenceEvent legacyPayload() {
        return new CreateUserPreferenceEvent(userId, List.of(genres), about);
    }

    boolean isAvro() {
        return avroRevision != null;
    }

    UserPreferenceChanged avroPayload() {
        return new UserPreferenceChanged(eventId, occurredAt, userId, avroRevision, List.of(genres), about);
    }

    String traceparent() {
        return traceparent;
    }

    void retryAfter(Instant instant) {
        nextAttemptAt = instant;
    }
}
