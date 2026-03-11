package com.mrs.catalog_service.module.book_list.application.command;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

public record UpdateBookListCommand(
        String name,
        String description,
        TypeBookList typeBookList
) {
}
