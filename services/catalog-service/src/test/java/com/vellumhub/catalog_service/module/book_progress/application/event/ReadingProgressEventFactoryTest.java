package com.vellumhub.catalog_service.module.book_progress.application.event;
import com.vellumhub.kafka.contracts.readingprogress.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReadingProgressEventFactoryTest {
    private final ReadingProgressEventFactory factory = new ReadingProgressEventFactory();
    @Test void newAggregateStartsAtZero() {
        var source = new CreateBookProgressEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "READING", 10);
        var event = factory.created(source);
        assertThat(event.kind()).isEqualTo(ReadingProgressChangedEvent.Kind.CREATED);
        assertThat(event.revision()).isZero();
        assertThat(event.eventId()).isEqualTo(source.eventId());
        assertThat(event.newPage()).isEqualTo(10);
    }
    @Test void updateCarriesPersistedVersion() {
        var source = new UpdateBookProgressEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "READING", 10, 20, 3);
        var event = factory.updated(source);
        assertThat(event.revision()).isEqualTo(3);
        assertThat(event.oldPage()).isEqualTo(10);
        assertThat(event.newPage()).isEqualTo(20);
        assertThat(event.kind()).isEqualTo(ReadingProgressChangedEvent.Kind.UPDATED);
    }
}

