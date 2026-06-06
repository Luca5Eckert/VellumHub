package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book_list.application.query.list.GetAllBookListQuery;
import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListPage;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class GetAllBookListUseCase {

    private final BookListRepository bookListRepository;

    public GetAllBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    public Page<BookList> execute(GetAllBookListQuery query){
        BookListFilter bookListFilter = BookListFilter.of(
                query.title(),
                query.description(),
                query.userOwnerList(),
                query.genres(),
                query.booksId(),
                query.typeBookList(),
                query.userAuthenticated()
        );
        BookListPage bookListPage = BookListPage.of(query.pageNumber(), query.pageSize());

        return bookListRepository.findAll(bookListFilter, bookListPage);
    }
}
