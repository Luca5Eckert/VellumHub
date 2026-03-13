package com.mrs.catalog_service.module.book_list.application.use_case;

import com.mrs.catalog_service.module.book_list.application.query.GetAllBookListQuery;
import com.mrs.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.mrs.catalog_service.module.book_list.domain.filter.BookListPage;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

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
