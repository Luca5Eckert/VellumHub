package com.mrs.catalog_service.module.book_list.presentation.dto;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

public record UpdateBookListRequest(
        String title,
        String description,
        TypeBookList typeBookList
) {
}
