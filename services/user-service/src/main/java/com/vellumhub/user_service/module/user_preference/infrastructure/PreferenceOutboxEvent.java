package com.vellumhub.user_service.module.user_preference.infrastructure;

import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
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
    private Instant occurredAt;
    private Instant nextAttemptAt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] genres;
    private String about;

    protected PreferenceOutboxEvent() {}

    PreferenceOutboxEvent(UUID userId, long revision, List<String> genres, String about) {
        eventId = UUID.randomUUID();
        occurredAt = Instant.now();
        nextAttemptAt = occurredAt;
        this.userId = userId;
        this.revision = revision;
        this.genres = genres.toArray(String[]::new);
        this.about = about;
    }

    CreateUserPreferenceEvent legacyPayload() {
        return new CreateUserPreferenceEvent(userId, List.of(genres), about);
    }

    void retryAfter(Instant instant) {
        nextAttemptAt = instant;
    }
}
