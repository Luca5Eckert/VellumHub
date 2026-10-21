package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.application.event.BookEventFactory;
import com.vellumhub.catalog_service.module.book.application.handler.update.UpdateBookHandler;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.exception.InvalidBookException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBookHandlerTest {
    @Mock BookRepository repository;
    @Mock GenreRepository genres;
    @Mock BookChangeWriter changes;
    @Mock VellumHubMetrics metrics;
    UpdateBookHandler handler;
    UUID id;
    Book book;

    @BeforeEach void setup() {
        id=UUID.randomUUID();
        book=Book.builder().id(id).title("Old").description("Description")
            .author("Author").isbn("ISBN").publisher("Publisher").pageCount(300)
            .releaseYear(2024).coverUrl("/old.png").genres(Set.of()).build();
        handler=new UpdateBookHandler(repository, genres, changes, new BookEventFactory(), metrics);
    }

    @Test void modifiedBookIsPersistedBeforeNotification() {
        when(repository.findById(id)).thenReturn(Optional.of(book));
        var request=new UpdateBookRequest("New",null,0,null,null,null,0,null,null);
        handler.execute(id,request);
        assertThat(book.getTitle()).isEqualTo("New");
        verify(repository).save(book);
        verify(changes).record(book,BookChangeKind.UPDATED);
        var order=inOrder(repository,changes);
        order.verify(repository).save(book);
        order.verify(changes).record(book,BookChangeKind.UPDATED);
    }

    @Test void identicalStateDoesNotEmitRevision() {
        when(repository.findById(id)).thenReturn(Optional.of(book));
        handler.execute(id,new UpdateBookRequest(null,null,0,null,null,null,0,null,null));
        verify(repository,never()).save(any());
        verifyNoInteractions(changes);
    }

    @Test void absentGenresKeepExistingCollection() {
        when(repository.findById(id)).thenReturn(Optional.of(book));
        handler.execute(id,new UpdateBookRequest(null,null,0,"/new.png",null,null,0,null,null));
        assertThat(book.getGenres()).isEmpty();
        verify(changes).record(book,BookChangeKind.UPDATED);
        verifyNoInteractions(genres);
    }

    @Test void duplicateIsRejectedBeforePersistence() {
        when(repository.findById(id)).thenReturn(Optional.of(book));
        when(repository.existByTitleAndAuthorAndIsbn(any(),any(),any())).thenReturn(true);
        assertThatThrownBy(()->handler.execute(id,new UpdateBookRequest(
            "Conflict",null,0,null,"Author","another-isbn",0,null,null)))
                .isInstanceOf(InvalidBookException.class);
        verifyNoInteractions(changes);
    }

    @Test void absentBookFailsWithoutEvent() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->handler.execute(id,new UpdateBookRequest(
            null,null,0,null,null,null,0,null,null)))
            .isInstanceOf(BookNotFoundException.class);
        verifyNoInteractions(changes);
    }

    @Test void unknownGenreIsRejected() {
        when(repository.findById(id)).thenReturn(Optional.of(book));
        when(genres.findByName("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(()->handler.execute(id,new UpdateBookRequest(
            null,null,0,null,null,null,0,null,Set.of("missing"))))
            .isInstanceOf(InvalidBookException.class);
        verifyNoInteractions(changes);
    }
}
