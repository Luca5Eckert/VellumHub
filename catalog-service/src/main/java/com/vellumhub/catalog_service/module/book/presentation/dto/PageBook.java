package com.mrs.catalog_service.module.book.presentation.dto;


public record PageBook(
        int pageSize,
        int pageNumber
) {

    public PageBook {
        pageSize = pageSize < 50 && pageSize > 0 ? pageSize : 10;
        pageNumber = Math.max(pageNumber, 0);
    }

}
