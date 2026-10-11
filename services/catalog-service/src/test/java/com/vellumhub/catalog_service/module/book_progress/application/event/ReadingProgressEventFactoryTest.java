package com.vellumhub.catalog_service.module.book_progress.application.event;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingProgressChange;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReadingProgressEventFactoryTest {
    private final ReadingProgressEventFactory factory = new ReadingProgressEventFactory();
    private ReadingProgressChange change(long revision, int before, int after, ReadingProgressChange.Kind kind) {
        return new ReadingProgressChange(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "READING", before, after, revision, kind);
    }
    @Test void creationStartsAtZero() {
        var source = change(0,0,10,ReadingProgressChange.Kind.CREATED);
        var event = factory.map(source);
        assertThat(event.kind()).isEqualTo(ReadingProgressChangedEvent.Kind.CREATED);
        assertThat(event.revision()).isZero();
        assertThat(event.eventId()).isEqualTo(source.eventId());
        assertThat(event.newPage()).isEqualTo(10);
    }
    @Test void updateCarriesPersistedRevision() {
        var event = factory.map(change(3,10,20,ReadingProgressChange.Kind.UPDATED));
        assertThat(event.revision()).isEqualTo(3);
        assertThat(event.oldPage()).isEqualTo(10);
        assertThat(event.newPage()).isEqualTo(20);
        assertThat(event.kind()).isEqualTo(ReadingProgressChangedEvent.Kind.UPDATED);
    }
}

