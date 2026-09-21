package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReadingProgressSerializationTest {
    // Models the record understood by a producer/consumer deployed before #192.
    record LegacyCreate(UUID bookProgressId, UUID userId, UUID bookId, String progress, int initPage) {}
    record LegacyUpdate(UUID bookProgressId, UUID userId, UUID bookId, String progress, int oldPage, int newPage) {}

    @Test
    void roundTripsBothAliasesAndSupportsOldConsumers() {
        UUID progress = UUID.randomUUID(), user = UUID.randomUUID(), book = UUID.randomUUID();
        var created = new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), progress, user, book, "READING", 10);
        var updated = new UpdateBookProgressEvent(UUID.randomUUID(), Instant.now(), progress, user, book, "READING", 10, 20);
        roundTrip(created, "create_book_progress_event", CreateBookProgressEvent.class);
        roundTrip(updated, "update_book_progress_event", UpdateBookProgressEvent.class);
        try (var serializer = new JsonSerializer<Object>();
             var oldCreate = new JsonDeserializer<>(LegacyCreate.class, false);
             var oldUpdate = new JsonDeserializer<>(LegacyUpdate.class, false)) {
            assertThat(oldCreate.deserialize("test", serializer.serialize("test", created)))
                    .isEqualTo(new LegacyCreate(progress, user, book, "READING", 10));
            assertThat(oldUpdate.deserialize("test", serializer.serialize("test", updated)))
                    .isEqualTo(new LegacyUpdate(progress, user, book, "READING", 10, 20));
        }
    }

    @Test
    void legacyJsonKeepsUnknownMetadataNull() {
        String json = """
                {"bookProgressId":"%s","userId":"%s","bookId":"%s","progress":"READING","initPage":10,"oldPage":10,"newPage":20}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (var created = new JsonDeserializer<>(CreateBookProgressEvent.class, false);
             var updated = new JsonDeserializer<>(UpdateBookProgressEvent.class, false)) {
            var a = created.deserialize("test", json.getBytes(StandardCharsets.UTF_8));
            var b = updated.deserialize("test", json.getBytes(StandardCharsets.UTF_8));
            assertThat(a.eventId()).isNull();
            assertThat(a.occurredAt()).isNull();
            assertThat(a.initPage()).isEqualTo(10);
            assertThat(b.eventId()).isNull();
            assertThat(b.occurredAt()).isNull();
            assertThat(b.newPage()).isEqualTo(20);
        }
    }

    private <T> void roundTrip(T event, String alias, Class<T> type) {
        var headers = new RecordHeaders();
        String mapping = alias + ":" + type.getName();
        try (var serializer = new JsonSerializer<T>(); var deserializer = new JsonDeserializer<T>()) {
            serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, mapping), false);
            deserializer.configure(Map.of(JsonDeserializer.TYPE_MAPPINGS, mapping,
                    JsonDeserializer.TRUSTED_PACKAGES, "com.vellumhub.kafka.contracts.readingprogress"), false);
            byte[] bytes = serializer.serialize("test", headers, event);
            assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8)).isEqualTo(alias);
            assertThat(deserializer.deserialize("test", headers, bytes)).isEqualTo(event);
        }
    }
}
