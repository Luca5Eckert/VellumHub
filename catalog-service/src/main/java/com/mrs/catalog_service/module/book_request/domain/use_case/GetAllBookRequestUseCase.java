package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class GetAllBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;

    public GetAllBookRequestUseCase(BookRequestRepository bookRequestRepository) {
        this.bookRequestRepository = bookRequestRepository;
    }

    public Page<BookRequest> getAll(
            int page,
            int size
    ){
        return bookRequestRepository.findAll(page, size);
    }

}
