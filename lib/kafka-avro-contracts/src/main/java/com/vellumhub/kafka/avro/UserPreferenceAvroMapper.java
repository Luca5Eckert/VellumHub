package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.avro.userpreference.UserPreferenceChangedEvent;
import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;

import java.time.Instant;
import java.util.UUID;

public final class UserPreferenceAvroMapper {
    private UserPreferenceAvroMapper() {}

    public static UserPreferenceChangedEvent toAvro(UserPreferenceChanged event) {
        return UserPreferenceChangedEvent.newBuilder()
                .setEventId(event.eventId().toString())
                .setOccurredAt(event.occurredAt().toString())
                .setUserId(event.userId().toString())
                .setRevision(event.revision())
                .setGenres(event.genres())
                .setAbout(event.about())
                .build();
    }

    public static UserPreferenceChanged fromAvro(String key, UserPreferenceChangedEvent event) {
        if (!event.getUserId().equals(key)) {
            throw new IllegalArgumentException("Preference record key does not match userId");
        }
        return new UserPreferenceChanged(UUID.fromString(event.getEventId()),
                Instant.parse(event.getOccurredAt()), UUID.fromString(event.getUserId()),
                event.getRevision(), event.getGenres(), event.getAbout());
    }
}
