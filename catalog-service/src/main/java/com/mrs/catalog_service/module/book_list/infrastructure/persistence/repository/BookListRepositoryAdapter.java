package com.mrs.catalog_service.module.book_list.infrastructure.persistence.repository;

import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class BookListRepositoryAdapter implements BookListRepository {

    private final JpaBookListRepository jpaBookListRepository;

    public BookListRepositoryAdapter(JpaBookListRepository jpaBookListRepository) {
        this.jpaBookListRepository = jpaBookListRepository;
    }

    @Override
    public Optional<BookList> findById(UUID id) {
        return jpaBookListRepository.findById(id);
    }

    @Override
    public BookList save(BookList bookList) {
        return jpaBookListRepository.save(bookList);
    }

    @Override
    public void deleteById(UUID id) {
        jpaBookListRepository.deleteById(id);
    }

    @Override
    public Optional<BookList> findByIdFull(UUID id) {
        return jpaBookListRepository.findByIdFull(id);
    }

}
