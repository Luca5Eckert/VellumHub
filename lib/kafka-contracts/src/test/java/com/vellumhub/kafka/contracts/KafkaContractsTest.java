package com.vellumhub.kafka.contracts;

import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.kafka.contracts.book.DeleteBookEvent;
import com.vellumhub.kafka.contracts.book.UpdateBookEvent;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaContractsTest {

    @Test
    void shouldExposeCanonicalTopicNames() {
        assertEquals("created-book", KafkaTopics.CREATED_BOOK);
        assertEquals("updated-book", KafkaTopics.UPDATED_BOOK);
        assertEquals("deleted-book", KafkaTopics.DELETED_BOOK);
        assertEquals("created-rating", KafkaTopics.CREATED_RATING);
        assertEquals("user-reaction-changed", KafkaTopics.USER_REACTION_CHANGED);
        assertEquals("created-user-preference", KafkaTopics.CREATED_USER_PREFERENCE);
        assertEquals("created-reading-progress", KafkaTopics.CREATED_READING_PROGRESS);
        assertEquals("updated-reading-progress", KafkaTopics.UPDATED_READING_PROGRESS);
        assertEquals(".*-dlt", KafkaTopics.DLT_PATTERN);
        assertEquals("-dlt", KafkaTopics.DLT_SUFFIX);
    }

    @Test
    void shouldExposeCanonicalTypeAliases() {
        assertEquals("create_book_event", KafkaTypeAliases.CREATE_BOOK_EVENT);
        assertEquals("update_book_event", KafkaTypeAliases.UPDATE_BOOK_EVENT);
        assertEquals("delete_book_event", KafkaTypeAliases.DELETE_BOOK_EVENT);
        assertEquals("create_rating_event", KafkaTypeAliases.CREATE_RATING_EVENT);
        assertEquals("reaction_changed_event", KafkaTypeAliases.REACTION_CHANGED_EVENT);
        assertEquals("created_user_preference", KafkaTypeAliases.CREATED_USER_PREFERENCE);
        assertEquals("create_book_progress_event", KafkaTypeAliases.CREATE_BOOK_PROGRESS_EVENT);
        assertEquals("update_book_progress_event", KafkaTypeAliases.UPDATE_BOOK_PROGRESS_EVENT);
    }

    @Test
    void shouldExposeCanonicalConsumerGroups() {
        assertEquals("engagement-service", KafkaConsumerGroups.ENGAGEMENT_SERVICE);
        assertEquals("engagement-service-dlt-group", KafkaConsumerGroups.ENGAGEMENT_SERVICE_DLT);
        assertEquals("recommendation-service", KafkaConsumerGroups.RECOMMENDATION_SERVICE);
        assertEquals("recommendation-service-dlt-group", KafkaConsumerGroups.RECOMMENDATION_SERVICE_DLT);
        assertEquals("recommendation_service_group", KafkaConsumerGroups.RECOMMENDATION_USER_PROFILE);
        assertEquals("recommendation-group", KafkaConsumerGroups.RECOMMENDATION_GROUP);
        assertEquals("recommendation-group-test", KafkaConsumerGroups.RECOMMENDATION_GROUP_TEST);
    }

    @Test
    void shouldInstantiateAllSharedEventPayloads() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID progressId = UUID.randomUUID();
        List<String> genres = List.of("Fantasy");

        assertNotNull(new CreateBookEvent(bookId, "Title", "Description", 2024, "cover", "Author", genres));
        assertNotNull(new UpdateBookEvent(bookId, "Title", "Description", 2024, "cover", "Author", genres));
        assertNotNull(new DeleteBookEvent(bookId));
        assertNotNull(new CreateBookProgressEvent(progressId, userId, bookId, "READING", 1));
        assertNotNull(new UpdateBookProgressEvent(progressId, userId, bookId, "READING", 1, 2));
        assertNotNull(new CreatedRatingEvent(userId, bookId, 5));
        assertNotNull(new ReactionChangedEvent(userId, bookId, "POSITIVE"));
        assertNotNull(new CreateUserPreferenceEvent(userId, genres, "About"));
    }
}
