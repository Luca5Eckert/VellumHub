package com.mrs.catalog_service.module.book_request.domain.port;

import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface BookRequestRepository {

    void save(BookRequest bookRequest);

    boolean existByTitleAndAuthor(String title, String author);

    Optional<BookRequest> findById(long requestId);

    void deleteById(long requestId);

    Page<BookRequest> findAll(int page, int size);

    boolean existsById(Long bookRequestId);
}
