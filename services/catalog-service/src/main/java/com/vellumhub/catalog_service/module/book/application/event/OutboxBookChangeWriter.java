package com.vellumhub.catalog_service.module.book.application.event;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Writes the Book change in the SAME transaction that persisted its optimistic revision. */
@Slf4j
@Component
public class OutboxBookChangeWriter implements BookChangeWriter {
    private final BookEventFactory events;
    private final OutboxWriter outbox;
    public OutboxBookChangeWriter(BookEventFactory events, OutboxWriter outbox) {
        this.events = events; this.outbox = outbox;
    }
    @Override
    public void record(Book book, BookChangeKind kind) {
        var event = events.changed(book, kind);
        outbox.append(OutboxMessage.book(event));
        log.debug("operation=book_outbox_enqueued, bookId={}, eventId={}, kind={}, revision={}",
                book.getId(), event.eventId(), kind, event.revision());
    }
}

