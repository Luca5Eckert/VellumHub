package com.vellumhub.catalog_service.module.book.domain.port;

import com.vellumhub.catalog_service.module.book.domain.model.Book;

import com.vellumhub.catalog_service.module.book.domain.model.ExternalBook;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBookSearchField;

import java.util.List;
import java.util.Optional;

public interface BookExternalProvider {

    Optional<Book> fetchByIsbn(String isbn);

    List<ExternalBook> search(String query, ExternalBookSearchField field);

    Optional<ExternalBook> fetchVolume(String volumeId);

}
