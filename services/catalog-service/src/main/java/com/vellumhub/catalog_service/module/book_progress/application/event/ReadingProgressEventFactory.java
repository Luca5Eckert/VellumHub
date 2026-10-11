package com.vellumhub.catalog_service.module.book_progress.application.event;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.stereotype.Component;
@Component
public class ReadingProgressEventFactory {
    public ReadingProgressChangedEvent created(CreateBookProgressEvent source) {
        return new ReadingProgressChangedEvent(
                source.eventId(), source.occurredAt(), source.bookProgressId(), source.userId(),
                source.bookId(), source.progress(), 0, source.initPage(), 0L,
                ReadingProgressChangedEvent.Kind.CREATED);
    }
    public ReadingProgressChangedEvent updated(UpdateBookProgressEvent source) {
        return new ReadingProgressChangedEvent(
                source.eventId(), source.occurredAt(), source.bookProgressId(), source.userId(),
                source.bookId(), source.progress(), source.oldPage(), source.newPage(), source.revision(),
                ReadingProgressChangedEvent.Kind.UPDATED);
    }
}

