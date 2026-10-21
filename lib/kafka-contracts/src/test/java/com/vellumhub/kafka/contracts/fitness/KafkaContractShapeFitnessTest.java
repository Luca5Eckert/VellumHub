package com.vellumhub.kafka.contracts.fitness;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AFF-CON-008 baseline: protect field presence and JVM field types for supported
 * canonical wire records. Additive evolution is allowed; removals/type changes
 * are prohibited. Actual JSON compatibility still requires serialized fixtures.
 */
class KafkaContractShapeFitnessTest {
    private static final Map<Class<?>, Map<String, Class<?>>> REQUIRED = Map.of(
            BookChangedEvent.class, Map.of(
                    "eventId", java.util.UUID.class, "occurredAt", java.time.Instant.class,
                    "bookId", java.util.UUID.class, "revision", long.class,
                    "kind", BookChangedEvent.Kind.class, "state", BookChangedEvent.State.class),
            RatingCreatedEvent.class, Map.of(
                    "eventId", java.util.UUID.class, "occurredAt", java.time.Instant.class,
                    "ratingId", Long.class, "userId", java.util.UUID.class,
                    "bookId", java.util.UUID.class, "stars", int.class, "revision", long.class),
            ReadingProgressChangedEvent.class, Map.of(
                    "eventId", java.util.UUID.class, "occurredAt", java.time.Instant.class,
                    "bookProgressId", java.util.UUID.class, "userId", java.util.UUID.class,
                    "bookId", java.util.UUID.class, "progress", String.class,
                    "oldPage", int.class, "newPage", int.class, "revision", long.class,
                    "kind", ReadingProgressChangedEvent.Kind.class),
            ReactionChangedEvent.class, Map.of(
                    "eventId", java.util.UUID.class, "occurredAt", java.time.Instant.class,
                    "reactionId", Long.class, "userId", java.util.UUID.class,
                    "bookId", java.util.UUID.class, "oldTypeReaction", String.class,
                    "newTypeReaction", String.class, "typeReaction", String.class,
                    "revision", Long.class)
    );

    @Test
    void canonicalEventsRetainSupportedFieldNamesAndTypes() {
        REQUIRED.forEach((record, requiredFields) -> {
            assertTrue(record.isRecord(), () -> record.getName() + " is no longer a record");
            var actual = Arrays.stream(record.getRecordComponents()).collect(
                    Collectors.toMap(RecordComponent::getName, RecordComponent::getType));
            requiredFields.forEach((field, type) ->
                    assertEquals(type, actual.get(field),
                            () -> "AFF-CON-008 incompatible schema: " +
                                    record.getSimpleName() + "." + field));
        });
    }

    @Test
    void deletionAndLegacyReactionContractsRemainRepresentable() {
        assertNotNull(BookChangedEvent.Kind.valueOf("DELETED"));
        assertNotNull(BookChangedEvent.Kind.valueOf("BASELINE"));
        assertNotNull(ReadingProgressChangedEvent.Kind.valueOf("BASELINE"));
        assertDoesNotThrow(() -> ReactionChangedEvent.class.getDeclaredConstructor(
                java.util.UUID.class, java.util.UUID.class, String.class));
    }
}
