package com.mrs.catalog_service.module.book.infrastructure.repository;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepositoryAdapter implements BookRepository {

    private final JpaBookRepository bookRepository;

    public BookRepositoryAdapter(JpaBookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void save(Book book) {
        bookRepository.save(book);
    }

    @Override
    public boolean existsById(UUID bookId) {
        return bookRepository.existsById(bookId);
    }

    @Override
    public Page<Book> findAll(PageRequest pageRequest) {
        return bookRepository.findAll(pageRequest);
    }

    @Override
    public Optional<Book> findById(UUID bookId) {
        return bookRepository.findById(bookId);
    }

    @Override
    public void deleteById(UUID bookId) {
        bookRepository.deleteById(bookId);
    }

    @Override
    public List<Book> findAllById(List<UUID> uuids) {
        return bookRepository.findAllById(uuids);
    }

    @Override
    public boolean existByTitleAndAuthor(String title, String author) {
        return bookRepository.existsByTitleAndAuthor(title, author);
    }

}
