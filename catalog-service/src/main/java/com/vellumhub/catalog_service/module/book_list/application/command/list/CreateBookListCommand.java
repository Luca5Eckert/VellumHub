package com.mrs.catalog_service.module.book_list.application.command.list;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

import java.util.List;
import java.util.UUID;

public record CreateBookListCommand(
        String title,
        String description,
        TypeBookList type,
        List<UUID> booksId,
        UUID userId
) {
    public static CreateBookListCommand of(String title, String description, TypeBookList type, List<UUID> booksId, UUID userId) {
        return new CreateBookListCommand(
                title,
                description,
                type,
                booksId,
                userId
        );
    }


}
