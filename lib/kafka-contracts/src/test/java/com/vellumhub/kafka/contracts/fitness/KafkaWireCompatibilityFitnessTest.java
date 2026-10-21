package com.vellumhub.kafka.contracts.fitness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

/** AFF-CON-008: immutable historical wire fixtures must be deserializable. */
class KafkaWireCompatibilityFitnessTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private <T> T fromFixture(String file, Class<T> type) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/contract-fixtures/v1/" + file)) {
            assertNotNull(input, "AFF-CON-008 missing fixture " + file);
            return mapper.readValue(input, type);
        }
    }

    @Test void previouslyPublishedBookEventIsReadable() throws Exception {
        var value = fromFixture("book-changed.json", BookChangedEvent.class);
        assertEquals("Dune", value.state().title());
        assertEquals(0, value.revision());
        assertEquals(BookChangedEvent.Kind.CREATED, value.kind());
    }

    @Test void previouslyPublishedProgressEventIsReadable() throws Exception {
        var value = fromFixture("reading-progress.json", ReadingProgressChangedEvent.class);
        assertEquals(25, value.newPage());
        assertEquals(0, value.revision());
    }

    @Test void previouslyPublishedRatingEventIsReadable() throws Exception {
        var value = fromFixture("rating-created.json", RatingCreatedEvent.class);
        assertEquals(42, value.ratingId());
        assertEquals(5, value.stars());
    }

    @Test void legacyReactionPayloadStillResolvesResultingType() throws Exception {
        var value = fromFixture("reaction-legacy.json", ReactionChangedEvent.class);
        assertEquals("LIKE", value.resultingTypeReaction());
    }
}
