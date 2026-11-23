package com.vellumhub.catalog_service.module.book.application.handler.get;

import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBook;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBookSearchField;
import com.vellumhub.catalog_service.module.book.domain.port.BookExternalProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class FindExternalBooksHandler {
    private final BookExternalProvider provider;

    public FindExternalBooksHandler(BookExternalProvider provider) {
        this.provider = provider;
    }

    public List<ExternalBook> search(String text, ExternalBookSearchField field) {
        String query = text.strip();
        if (query.isEmpty() || query.length() > 200) {
            throw new BookDomainException("Search text must be between 1 and 200 characters");
        }
        return provider.search(query, field);
    }

    public Book fetchVolume(String volumeId) {
        if (!volumeId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new BookDomainException("Invalid Google Books volume ID");
        }
        ExternalBook volume = provider.fetchVolume(volumeId).orElseThrow(BookNotFoundException::new);
        if (volume.isbn() == null) {
            throw new BookDomainException("The selected volume has no ISBN");
        }
        return Book.create(volume.title(), volume.description(), zeroIfMissing(volume.releaseYear()),
                volume.author(), volume.isbn(), zeroIfMissing(volume.pageCount()), volume.publisher(),
                volume.coverUrl(), Set.of());
    }

    private int zeroIfMissing(Integer value) {
        return value == null ? 0 : value;
    }
}
