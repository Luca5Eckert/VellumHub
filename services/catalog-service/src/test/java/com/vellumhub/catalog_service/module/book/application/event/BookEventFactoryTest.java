package com.vellumhub.catalog_service.module.book.application.event;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BookEventFactoryTest {
    private final BookEventFactory factory=new BookEventFactory();
    private Book book() {
        return Book.builder().id(UUID.randomUUID()).version(2).title("Dune")
            .description("A desert planet").author("Frank Herbert")
            .isbn("9780441013593").publisher("Ace").pageCount(412)
            .releaseYear(1965).coverUrl("/covers/123.png").genres(Set.of()).build();
    }
    @Test void snapshotsContainAllPublicFieldsWithStableRevision() {
        Book book=book();
        var event=factory.changed(book, BookChangeKind.UPDATED);
        assertThat(event.kind()).isEqualTo(BookChangedEvent.Kind.UPDATED);
        assertThat(event.revision()).isEqualTo(2);
        assertThat(event.bookId()).isEqualTo(book.getId());
        assertThat(event.state().isbn()).isEqualTo(book.getIsbn());
        assertThat(event.state().pageCount()).isEqualTo(412);
        assertThat(event.state().publisher()).isEqualTo("Ace");
        assertThat(event.state().genres()).isEmpty();
    }
    @Test void deletionIsTombstoneNotReplayedBookState() {
        var event=factory.changed(book(),BookChangeKind.DELETED);
        assertThat(event.state()).isNull();
        assertThat(event.kind()).isEqualTo(BookChangedEvent.Kind.DELETED);
    }
}

