package com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.repository.list;

import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListPage;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.specification.BookListSpecificationProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class BookListRepositoryAdapter implements BookListRepository {

    private final JpaBookListRepository jpaBookListRepository;
    private final BookListSpecificationProvider bookListSpecificationProvider;

    public BookListRepositoryAdapter(JpaBookListRepository jpaBookListRepository, BookListSpecificationProvider bookListSpecificationProvider) {
        this.jpaBookListRepository = jpaBookListRepository;
        this.bookListSpecificationProvider = bookListSpecificationProvider;
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

    @Override
    public Page<BookList> findAll(BookListFilter bookListFilter, BookListPage bookListPage) {
        var filterSpecification = bookListSpecificationProvider.of(bookListFilter);
        Pageable pageRequest = PageRequest.of(bookListPage.pageNumber(), bookListPage.pageSize());

        return jpaBookListRepository.findAll(filterSpecification, pageRequest);
    }

}
