package com.vellumhub.catalog_service.module.book_list.application.command.list;

import com.vellumhub.catalog_service.module.book_list.domain.model.TypeBookList;

import java.util.UUID;

public record UpdateBookListCommand(
        UUID userId,
        UUID bookListId,
        String name,
        String description,
        TypeBookList typeBookList
) {
    public static UpdateBookListCommand of(
            String name,
            String description,
            TypeBookList type,
            UUID bookListId,
            UUID userId
    ) {

        return new UpdateBookListCommand(
                userId,
                bookListId,
                name,
                description,
                type
        );
    }

}
