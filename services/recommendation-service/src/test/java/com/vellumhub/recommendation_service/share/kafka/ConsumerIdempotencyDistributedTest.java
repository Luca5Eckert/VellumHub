package com.vellumhub.recommendation_service.share.kafka;

import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.recommendation_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.module.reading_progress.application.use_case.ProcessOrderedReadingProgressUseCase;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer.EngagementEventProcessor;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Issue #200: the real PostgreSQL transaction, Kafka re-delivery and unique constraints
 * define correctness; mocks are limited to deterministic embedding inference.
 */
@Tag("distributed")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.kafka.legacy-consumers.enabled=false")
class ConsumerIdempotencyDistributedTest extends DistributedIntegrationTestSupport {
    @Autowired private ApplyBookEventUseCase books;
    @Autowired private ProcessOrderedReadingProgressUseCase progress;
    @Autowired private EngagementEventProcessor engagement;
    @Autowired private BookFeatureRepository features;
    @Autowired private UserProfileRepository profiles;
    @Autowired private KafkaTemplate<String,Object> kafka;
    @Autowired private KafkaListenerEndpointRegistry kafkaListenerRegistry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MeterRegistry registry;

    @MockitoBean private EmbeddingBookProvider embeddings;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM kafka_consumer_quarantine");
        jdbc.update("DELETE FROM processed_engagement_events");
        jdbc.update("DELETE FROM engagement_event_offsets");
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM book_event_offsets");
        jdbc.update("DELETE FROM reading_progress_offsets");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM recommendation_genres");
        jdbc.update("DELETE FROM recommendations");
        jdbc.update("DELETE FROM book_features");
        reset(embeddings);
        when(embeddings.of(any(),any(),any(),any())).thenReturn(vector());
    }

    @Test
    void exhaustedOrderedBookFailureGoesToDltButNeverAcknowledgesRevision() throws Exception {
        UUID book=UUID.randomUUID();
        var event=bookEvent(book);
        doThrow(new IllegalStateException("forced recovery scenario"))
                .when(embeddings).of(any(),any(),any(),any());

        String dltTopic=KafkaTopics.BOOK_EVENTS+".recommendation-service-dlt";
        try (var dlt=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),dltTopic,
                "ordered-book-recovery")) {
            // Separate group: observing DLT does not affect the canonical consumer.
            kafka.send(KafkaTopics.BOOK_EVENTS,book.toString(),event).get(5,TimeUnit.SECONDS);
            var dead=dlt.awaitRecord(dltTopic,book.toString(),
                    Duration.ofSeconds(20), Duration.ofMillis(100));
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-topic"))
                    .containsExactly(KafkaTopics.BOOK_EVENTS);
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.RECOMMENDATION_SERVICE);
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM kafka_consumer_quarantine
                    WHERE consumer_group=? AND source_topic=? AND status='BLOCKED'
                    """, Integer.class,KafkaConsumerGroups.RECOMMENDATION_SERVICE,
                    KafkaTopics.BOOK_EVENTS)).isEqualTo(1);
        });
        assertThat(count("processed_events","event_id",event.eventId())).isZero();
        assertThat(count("book_event_offsets","book_id",book)).isZero();
    }

    @Test
    @DisplayName("AFF-EVT-006 — Book redelivery has one durable effect")
    void bookRedeliveryDoesNotRebuildVectorAndPersistsEventIdentity() {
        var event=bookEvent(UUID.randomUUID());
        assertThat(books.execute(event)).isEqualTo(EventProcessingOutcome.APPLIED);
        assertThat(books.execute(event)).isEqualTo(EventProcessingOutcome.DUPLICATE);
        assertThat(count("processed_events", "event_id", event.eventId())).isEqualTo(1);
        assertThat(count("book_event_offsets", "book_id", event.bookId())).isEqualTo(1);
        verify(embeddings,times(1)).of(any(),any(),any(),any());
    }

    @Test
    @DisplayName("AFF-EVT-006 — failed projection rolls back checkpoint")
    void failedBookProjectionRollsBackBothCheckpointAndProcessedEvent() {
        var event=bookEvent(UUID.randomUUID());
        when(embeddings.of(any(),any(),any(),any()))
                .thenThrow(new IllegalStateException("injected inference failure"));
        assertThatThrownBy(() -> books.execute(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected inference failure");
        assertThat(count("processed_events", "event_id", event.eventId())).isZero();
        assertThat(count("book_event_offsets", "book_id", event.bookId())).isZero();
        assertThat(count("recommendations", "book_id", event.bookId())).isZero();
        doReturn(vector()).when(embeddings).of(any(),any(),any(),any());
        assertThat(books.execute(event)).isEqualTo(EventProcessingOutcome.APPLIED);
    }

    @Test
    void concurrentBookRedeliverySerializesOnCheckpoint() throws Exception {
        var event=bookEvent(UUID.randomUUID());
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(() -> books.execute(event));
            var second=workers.submit(() -> books.execute(event));
            assertThat(List.of(first.get(15,TimeUnit.SECONDS), second.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(EventProcessingOutcome.APPLIED,EventProcessingOutcome.DUPLICATE);
        }
        assertThat(count("processed_events","event_id",event.eventId())).isEqualTo(1);
        verify(embeddings,times(1)).of(any(),any(),any(),any());
    }

    @Test
    @DisplayName("AFF-EVT-006 — ReadingProgress redelivery is idempotent")
    void readingProgressRedeliveryCannotIncreaseUserProfileTwice() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID(),aggregate=UUID.randomUUID();
        saveBookFeature(book);
        var event=new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),aggregate,
                user,book,"READING",0,80,0,ReadingProgressChangedEvent.Kind.CREATED);
        assertThat(progress.execute(event)).isEqualTo(EventProcessingOutcome.APPLIED);
        double firstScore=profiles.findById(user).orElseThrow().getTotalEngagementScore();
        assertThat(progress.execute(event)).isEqualTo(EventProcessingOutcome.DUPLICATE);
        assertThat(profiles.findById(user).orElseThrow().getTotalEngagementScore()).isEqualTo(firstScore);
        assertThat(count("processed_events","event_id",event.eventId())).isEqualTo(1);
    }

    @Test
    @DisplayName("AFF-EVT-006 — missing dependency prevents checkpoint")
    void missingBookFeatureRollsBackReadingProgressCheckpoint() {
        var event=new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),"READING",0,80,0,
                ReadingProgressChangedEvent.Kind.CREATED);
        assertThatThrownBy(() -> progress.execute(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Book features not yet available");
        assertThat(count("processed_events","event_id",event.eventId())).isZero();
        assertThat(count("reading_progress_offsets","aggregate_id",event.bookProgressId())).isZero();
    }

    @Test
    @DisplayName("AFF-EVT-006 — event identity collision rejects second aggregate")
    void engagementIdentityCollisionDoesNotAcknowledgeSecondAggregate() {
        UUID id=UUID.randomUUID(),user=UUID.randomUUID(),book=UUID.randomUUID();
        saveBookFeature(book);
        var first=new RatingCreatedEvent(id,Instant.now(),991L,user,book,5,0);
        var second=new RatingCreatedEvent(id,Instant.now(),992L,user,book,5,0);
        assertThat(engagement.apply(first)).isEqualTo(EventProcessingOutcome.APPLIED);
        double firstScore=profiles.findById(user).orElseThrow().getTotalEngagementScore();
        assertThatThrownBy(() -> engagement.apply(second))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Event ID collision");
        assertThat(count("processed_engagement_events","event_id",id)).isEqualTo(1);
        assertThat(profiles.findById(user).orElseThrow().getTotalEngagementScore()).isEqualTo(firstScore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM engagement_event_offsets WHERE aggregate_id = '992'",
                Integer.class)).isZero();
    }

    @Test
    void reusingBookEventIdForAnotherAggregateRollsBackTheSecondProjection() {
        UUID eventId=UUID.randomUUID(),firstBook=UUID.randomUUID(),secondBook=UUID.randomUUID();
        var original=bookEvent(firstBook);
        var first=new BookChangedEvent(eventId,original.occurredAt(),firstBook,0,
                original.kind(),original.state());
        var second=new BookChangedEvent(eventId,Instant.now(),secondBook,0,
                original.kind(),original.state());
        assertThat(books.execute(first)).isEqualTo(EventProcessingOutcome.APPLIED);
        assertThatThrownBy(() -> books.execute(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("processed_events","event_id",eventId)).isEqualTo(1);
        assertThat(count("book_event_offsets","book_id",secondBook)).isZero();
        assertThat(count("recommendations","book_id",secondBook)).isZero();
    }

    @Test
    void failedRatingDeltaDoesNotCommitIdentityOrRevisionAndCanBeRetried() {
        UUID user=UUID.randomUUID(),book=UUID.randomUUID(),eventId=UUID.randomUUID();
        var fact=new RatingCreatedEvent(eventId,Instant.now(),994L,user,book,5,0);
        assertThatThrownBy(() -> engagement.apply(fact))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Book features unavailable");
        assertThat(count("processed_engagement_events","event_id",eventId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM engagement_event_offsets WHERE aggregate_id = '994'",
                Integer.class)).isZero();
        saveBookFeature(book);
        assertThat(engagement.apply(fact)).isEqualTo(EventProcessingOutcome.APPLIED);
        assertThat(count("processed_engagement_events","event_id",eventId)).isEqualTo(1);
        assertThat(profiles.findById(user)).isPresent();
    }

    @Test
    @DisplayName("AFF-EVT-006 — Kafka redelivery has no duplicate rating effect")
    void ratingRedeliveryThroughRealKafkaIsAcknowledgedWithoutAnotherProfileDelta() throws Exception {
        UUID id=UUID.randomUUID(),user=UUID.randomUUID(),book=UUID.randomUUID();
        saveBookFeature(book);
        var event=new RatingCreatedEvent(id,Instant.now(),993L,user,book,5,0);
        // Several shared-group listeners join during context startup; wait for the
        // canonical consumer's topic assignment, not an arbitrary fixed sleep.
        await().atMost(Duration.ofSeconds(45)).until(() ->
                kafkaListenerRegistry.getListenerContainers().stream().anyMatch(container ->
                        container.getAssignedPartitions() != null
                        && container.getAssignedPartitions().stream().anyMatch(partition ->
                                KafkaTopics.ENGAGEMENT_RATINGS.equals(partition.topic()))));
        try (KafkaProbe probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                KafkaTopics.ENGAGEMENT_RATINGS, "rating-idempotency")) {
            kafka.send(KafkaTopics.ENGAGEMENT_RATINGS,user.toString(),event).get(5,TimeUnit.SECONDS);
            var delivered = probe.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS, user.toString(),
                    Duration.ofSeconds(10), Duration.ofMillis(100));
            assertThat(KafkaProbe.utf8HeaderValues(delivered, "__TypeId__"))
                    .containsExactly("rating_created_event");
        }
        await().atMost(ASYNC_TIMEOUT).pollInterval(ASYNC_POLL_INTERVAL).untilAsserted(() -> {
            assertThat(count("processed_engagement_events","event_id",id)).isEqualTo(1);
            assertThat(profiles.findById(user)).isPresent();
        });
        double score=profiles.findById(user).orElseThrow().getTotalEngagementScore();
        double previousDuplicates=duplicateCount();
        kafka.send(KafkaTopics.ENGAGEMENT_RATINGS,user.toString(),event).get(5,TimeUnit.SECONDS);
        await().atMost(ASYNC_TIMEOUT).pollInterval(ASYNC_POLL_INTERVAL)
                .untilAsserted(() -> assertThat(duplicateCount()).isGreaterThan(previousDuplicates));
        assertThat(count("processed_engagement_events","event_id",id)).isEqualTo(1);
        assertThat(profiles.findById(user).orElseThrow().getTotalEngagementScore()).isEqualTo(score);
    }


    /** AFF-EVT-007: a future Book revision must not advance the durable offset. */
    @Test
    void bookRevisionGapDoesNotAdvancePostgresCheckpoint() {
        UUID bookId = UUID.randomUUID();
        var initial = bookEvent(bookId);
        assertThat(books.execute(initial)).isEqualTo(EventProcessingOutcome.APPLIED);
        var future = new BookChangedEvent(UUID.randomUUID(), Instant.now(), bookId, 3,
                BookChangedEvent.Kind.UPDATED, initial.state());
        assertThatThrownBy(() -> books.execute(future)).isInstanceOf(IllegalStateException.class);
        assertThat(count("book_event_offsets", "book_id", bookId)).isEqualTo(1);
        assertThat(count("processed_events", "event_id", future.eventId())).isZero();
        verify(embeddings, times(1)).of(any(), any(), any(), any());
    }

    /** AFF-EVT-007: reading progress must not jump over a missing revision. */
    @Test
    void readingProgressRevisionGapDoesNotChangeProfileOrCheckpoint() {
        UUID userId = UUID.randomUUID(), bookId = UUID.randomUUID(), aggregateId = UUID.randomUUID();
        saveBookFeature(bookId);
        var initial = new ReadingProgressChangedEvent(UUID.randomUUID(), Instant.now(),
                aggregateId, userId, bookId, "READING", 0, 40, 0,
                ReadingProgressChangedEvent.Kind.CREATED);
        assertThat(progress.execute(initial)).isEqualTo(EventProcessingOutcome.APPLIED);
        double score = profiles.findById(userId).orElseThrow().getTotalEngagementScore();
        var future = new ReadingProgressChangedEvent(UUID.randomUUID(), Instant.now(),
                aggregateId, userId, bookId, "READING", 40, 80, 3,
                ReadingProgressChangedEvent.Kind.UPDATED);
        assertThatThrownBy(() -> progress.execute(future)).isInstanceOf(IllegalStateException.class);
        assertThat(count("reading_progress_offsets", "aggregate_id", aggregateId)).isEqualTo(1);
        assertThat(count("processed_events", "event_id", future.eventId())).isZero();
        assertThat(profiles.findById(userId).orElseThrow().getTotalEngagementScore()).isEqualTo(score);
    }

    private double duplicateCount() {
        var counter=registry.find(VellumHubMetrics.KAFKA_EVENTS_DUPLICATE)
                .tags("topic","engagement-events","event_type","RatingCreatedEvent",
                        "consumer_group",KafkaConsumerGroups.RECOMMENDATION_SERVICE).counter();
        return counter==null?0:counter.count();
    }

    private int count(String table,String field,UUID id) {
        // Only constant call-site identifiers are used, never request-driven SQL identifiers.
        return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+field+" = ?",
                Integer.class,id);
    }

    private void saveBookFeature(UUID book) {
        features.save(BookFeature.create(book,vector(),1.0));
    }

    private static float[] vector() {
        float[] result=new float[384];
        result[0]=1.0f;
        return result;
    }

    private BookChangedEvent bookEvent(UUID id) {
        var snapshot=new BookChangedEvent.State("Dune","Science fiction",1965,null,
                "Frank Herbert","ISBN",400,"Ace",List.of("Science fiction"));
        return new BookChangedEvent(UUID.randomUUID(),Instant.now(),id,0,
                BookChangedEvent.Kind.CREATED,snapshot);
    }
}
