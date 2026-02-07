package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class ApproveBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;
    private final BookRepository bookRepository;

    public ApproveBookRequestUseCase(BookRequestRepository bookRequestRepository, BookRepository bookRepository) {
        this.bookRequestRepository = bookRequestRepository;
        this.bookRepository = bookRepository;
    }

    public void execute(long requestId) {
        BookRequest bookRequest = bookRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Book request not found"));

        Book book =
}
