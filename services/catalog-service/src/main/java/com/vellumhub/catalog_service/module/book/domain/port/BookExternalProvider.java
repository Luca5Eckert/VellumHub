package com.vellumhub.catalog_service.module.book.domain.port;

import com.vellumhub.catalog_service.module.book.domain.model.Book;

import java.util.Optional;

public interface BookExternalProvider {

    Optional<Book> fetchByIsbn(String isbn);

}
