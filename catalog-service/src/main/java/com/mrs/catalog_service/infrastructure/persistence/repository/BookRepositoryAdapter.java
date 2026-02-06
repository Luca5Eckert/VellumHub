package com.mrs.catalog_service.infrastructure.persistence.repository;

import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepositoryAdapter implements BookRepository {

    private final JpaBookRepository mediaRepositoryJpa;

    public BookRepositoryAdapter(JpaBookRepository mediaRepositoryJpa) {
        this.mediaRepositoryJpa = mediaRepositoryJpa;
    }

    @Override
    public void save(Book book) {
        mediaRepositoryJpa.save(book);
    }

    @Override
    public boolean existsById(UUID bookId) {
        return mediaRepositoryJpa.existsById(bookId);
    }

    @Override
    public Page<Book> findAll(PageRequest pageRequest) {
        return mediaRepositoryJpa.findAll(pageRequest);
    }

    @Override
    public Optional<Book> findById(UUID bookId) {
        return mediaRepositoryJpa.findById(bookId);
    }

    @Override
    public void deleteById(UUID bookId) {
        mediaRepositoryJpa.deleteById(bookId);
    }

    @Override
    public List<Book> findAllById(List<UUID> uuids) {
        return mediaRepositoryJpa.findAllById(uuids);
    }

}
