package com.mrs.catalog_service.module.book_list.domain.filter;

public record BookListPage(
        int pageNumber,
        int pageSize
) {

    public BookListPage {
        if(pageNumber < 0) pageNumber = 0;
        if(pageSize < 1) pageSize = 1;
        if(pageSize > 100) pageSize = 100;
    }

    public static BookListPage of(int pageNumber, int pageSize){
        return new BookListPage(
                pageNumber, pageSize
        );
    }
}
