package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.recommendation_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderedBookAvroConsumerTest {
    @Test void forwardsOriginalIdentityToExistingProjectionAndRejectsMismatchedKey() {
        UUID book = UUID.randomUUID();
        var event = new BookChangedEvent(UUID.randomUUID(), Instant.parse("2026-11-07T12:00:00Z"),
                book, 0L, BookChangedEvent.Kind.CREATED,
                new BookChangedEvent.State("Dune", null, 1965, null,
                        "Herbert", null, 400, null, List.of("SF")));
        var projection = mock(ApplyBookEventUseCase.class);
        var decoder = mock(KafkaAvroDeserializer.class);
        byte[] bytes = new byte[] {0, 1, 2, 3, 4};
        when(decoder.deserialize(KafkaTopics.BOOK_EVENTS_AVRO, bytes))
                .thenReturn(BookAvroMapper.encode(event));
        when(projection.execute(event)).thenReturn(com.vellumhub.recommendation_service.share.application.EventProcessingOutcome.APPLIED);
        var consumer = new OrderedBookAvroConsumer(projection,
                new VellumHubMetrics(new SimpleMeterRegistry()), decoder);
        consumer.consume(new ConsumerRecord<>(KafkaTopics.BOOK_EVENTS_AVRO,
                0, 1L, book.toString(), bytes));
        verify(projection, times(1)).execute(event);
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(
                new ConsumerRecord<>(KafkaTopics.BOOK_EVENTS_AVRO,
                        0, 2L, UUID.randomUUID().toString(), bytes)));
        verify(projection, times(1)).execute(event);
    }
}
