package com.vellumhub.catalog_service.module.book_progress.application.event;

import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingProgressChange;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.stereotype.Component;

/** This is the only translation boundary from the Catalog use-case result to Kafka. */
@Component
public class ReadingProgressEventFactory {
    public ReadingProgressChangedEvent map(ReadingProgressChange change) {
        return new ReadingProgressChangedEvent(
            change.eventId(), change.occurredAt(), change.bookProgressId(),
            change.userId(), change.bookId(), change.progress(), change.oldPage(),
            change.newPage(), change.revision(),
            ReadingProgressChangedEvent.Kind.valueOf(change.kind().name()));
    }
}

