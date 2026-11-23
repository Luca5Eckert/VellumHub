package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.application.handler.create.CreateBookWithIsbnHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.FindExternalBooksHandler;
import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBook;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.port.BookExternalProvider;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExternalBookImportTest {

    private final BookExternalProvider provider = mock(BookExternalProvider.class);
    private final BookRepository repository = mock(BookRepository.class);
    private final BookChangeWriter events = mock(BookChangeWriter.class);
    private final FindExternalBooksHandler lookup = new FindExternalBooksHandler(provider);
    private final CreateBookWithIsbnHandler creation = new CreateBookWithIsbnHandler(repository, provider, events);

    @Test
    void savesTheSelectedSnapshotAndRecordsItsCreationInOrder() {
        when(provider.fetchVolume("selected")).thenReturn(Optional.of(volume("9780132350884")));

        var book = lookup.fetchVolume("selected");
        creation.saveImportedVolume(book);

        assertThat(book.getTitle()).isEqualTo("Selected title");
        assertThat(book.getIsbn()).isEqualTo("9780132350884");
        var ordered = inOrder(repository, events);
        ordered.verify(repository).save(book);
        ordered.verify(events).record(book, BookChangeKind.CREATED);
    }

    @Test
    void rejectsInvalidMissingAndUnimportableVolumesBeforeWriting() {
        when(provider.fetchVolume("missing")).thenReturn(Optional.empty());
        when(provider.fetchVolume("no-isbn")).thenReturn(Optional.of(volume(null)));

        assertThatThrownBy(() -> lookup.fetchVolume("../hidden")).isInstanceOf(BookDomainException.class);
        assertThatThrownBy(() -> lookup.fetchVolume("missing")).isInstanceOf(BookNotFoundException.class);
        assertThatThrownBy(() -> lookup.fetchVolume("no-isbn")).isInstanceOf(BookDomainException.class);
        verifyNoInteractions(repository, events);
        verify(provider, never()).fetchVolume("../hidden");
    }

    @Test
    void rejectsExistingIsbnWithoutWritingAnotherEvent() {
        when(provider.fetchVolume("selected")).thenReturn(Optional.of(volume("9780132350884")));
        when(repository.existsByIsbn("9780132350884")).thenReturn(true);

        assertThatThrownBy(() -> creation.saveImportedVolume(lookup.fetchVolume("selected")))
                .isInstanceOf(BookDomainException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    private ExternalBook volume(String isbn) {
        return new ExternalBook("selected", "Selected title", "Test author", null,
                2024, 200, isbn, "Test press", null);
    }
}
