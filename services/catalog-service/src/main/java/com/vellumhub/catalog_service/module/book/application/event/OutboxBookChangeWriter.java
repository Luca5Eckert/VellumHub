package com.vellumhub.catalog_service.module.book.application.event;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** Writes the Book change in the SAME transaction that persisted its optimistic revision. */
@Slf4j
@Component
public class OutboxBookChangeWriter implements BookChangeWriter {
    private final BookEventFactory events;
    private final OutboxWriter outbox;
    private final boolean writeAvro;
    @Autowired
    public OutboxBookChangeWriter(BookEventFactory events, OutboxWriter outbox,
            @Value("${app.book.events.wire-format:json}") String format) {
        this.events = events; this.outbox = outbox;
        if (!format.equalsIgnoreCase("json") && !format.equalsIgnoreCase("avro"))
            throw new IllegalArgumentException("Unsupported Book wire format");
        this.writeAvro = format.equalsIgnoreCase("avro");
    }
    public OutboxBookChangeWriter(BookEventFactory events, OutboxWriter outbox) {
        this(events, outbox, "json");
    }
    @Override
    public void record(Book book, BookChangeKind kind) {
        var event = events.changed(book, kind);
        outbox.append(writeAvro ? OutboxMessage.bookAvro(event) : OutboxMessage.book(event));
        log.debug("operation=book_outbox_enqueued, bookId={}, eventId={}, kind={}, revision={}",
                book.getId(), event.eventId(), kind, event.revision());
    }
}

