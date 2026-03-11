package com.mrs.catalog_service.module.book_list.application.command;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

import java.util.UUID;

public record UpdateBookListCommand(
        UUID bookListId,
        String name,
        String description,
        TypeBookList typeBookList
) {
}
