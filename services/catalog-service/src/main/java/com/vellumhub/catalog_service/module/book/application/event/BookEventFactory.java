package com.vellumhub.catalog_service.module.book.application.event;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Maps persisted Catalog state to a complete downstream snapshot; no repositories or transport. */
@Component
public class BookEventFactory {
    public BookChangedEvent changed(Book book, BookChangeKind kind) {
        return new BookChangedEvent(UUID.randomUUID(), Instant.now(), book.getId(), book.getVersion(),
                BookChangedEvent.Kind.valueOf(kind.name()),
                kind == BookChangeKind.DELETED ? null : snapshot(book));
    }

    public BookChangedEvent.State snapshot(Book book) {
        List<String> genres = book.getGenres() == null ? List.of() :
                book.getGenres().stream().map(Genre::getName).sorted().toList();
        return new BookChangedEvent.State(
                book.getTitle(), book.getDescription(), book.getReleaseYear(),
                book.getCoverUrl(), book.getAuthor(), book.getIsbn(),
                book.getPageCount(), book.getPublisher(), genres
        );
    }
}

