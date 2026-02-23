package com.mrs.catalog_service.module.book_request.infrastructure.persistence.repository;

import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BookRequestRepositoryAdapter implements BookRequestRepository {

    private final BookRequestRepositoryJpa bookRequestRepositoryJpa;

    public BookRequestRepositoryAdapter(BookRequestRepositoryJpa bookRequestRepositoryJpa) {
        this.bookRequestRepositoryJpa = bookRequestRepositoryJpa;
    }

    @Override
    public void save(BookRequest bookRequest) {
        bookRequestRepositoryJpa.save(bookRequest);
    }

    @Override
    public boolean existByTitleAndAuthor(String title, String author) {
        return bookRequestRepositoryJpa.existsByTitleAndAuthor(title, author);
    }

    @Override
    public Optional<BookRequest> findById(long requestId) {
        return bookRequestRepositoryJpa.findById(requestId);
    }

    @Override
    public void deleteById(long requestId) {
        bookRequestRepositoryJpa.deleteById(requestId);
    }

    @Override
    public Page<BookRequest> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        return bookRequestRepositoryJpa.findAll(pageRequest);
    }

}
