package com.mrs.catalog_service.module.book_list.domain.port;

import com.mrs.catalog_service.module.book_list.domain.model.BookList;

public interface BookListRepository {
    BookList save(BookList bookList);
}
