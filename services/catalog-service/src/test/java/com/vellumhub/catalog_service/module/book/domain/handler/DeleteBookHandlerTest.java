package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.application.handler.DeleteBookHandler;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotExistException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteBookHandlerTest {
    @Mock BookRepository repository;
    @Mock BookChangeWriter changes;
    @Mock VellumHubMetrics metrics;
    @InjectMocks DeleteBookHandler handler;

    @Test void softDeletePreservesRowAndEmitsTombstone() {
        UUID id=UUID.randomUUID();
        Book book=Book.builder().id(id).title("Dune").build();
        when(repository.findById(id)).thenReturn(Optional.of(book));
        handler.execute(id);
        assertThat(book.getDeletedAt()).isNotNull();
        verify(repository).save(book);
        verify(repository,never()).deleteById(any());
        verify(changes).record(book,BookChangeKind.DELETED);
    }

    @Test void missingBookDoesNotEmit() {
        UUID id=UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->handler.execute(id)).isInstanceOf(BookNotExistException.class);
        verifyNoInteractions(changes);
        verify(repository,never()).save(any());
    }
}

