package com.vellumhub.kafka.contracts.user;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UserPreferenceChanged(UUID eventId, Instant occurredAt, UUID userId,
                                    long revision, List<String> genres, String about) {
    public UserPreferenceChanged {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(userId);
        if (revision < 0) {
            throw new IllegalArgumentException("Preference revision cannot be negative");
        }
        genres = List.copyOf(genres);
    }
}
