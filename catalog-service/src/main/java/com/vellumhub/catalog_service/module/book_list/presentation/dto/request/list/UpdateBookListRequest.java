package com.mrs.catalog_service.module.book_list.presentation.dto.request.list;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

public record UpdateBookListRequest(
        String title,
        String description,
        TypeBookList typeBookList
) {
}
