package com.mrs.catalog_service.module.book.domain.port;

import com.mrs.catalog_service.module.book.domain.model.Book;

import java.util.Optional;

public interface BookExternalProvider {

    Optional<Book> fetchByIsbn(String isbn);

}
