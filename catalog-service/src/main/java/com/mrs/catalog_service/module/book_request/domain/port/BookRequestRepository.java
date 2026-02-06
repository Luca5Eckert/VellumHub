package com.mrs.catalog_service.module.book_request.domain.port;

import com.mrs.catalog_service.module.book_request.domain.BookRequest;

public interface BookRequestRepository {

    void save(BookRequest bookRequest);

    boolean existByTitleAndAuthor(String title, String author);
}
