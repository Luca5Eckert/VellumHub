package com.vellumhub.catalog_service.module.book_list.application.command.list;

import java.util.UUID;

public record DeleteBookListCommand(
        UUID userId,
        UUID bookListId
) {

    public static DeleteBookListCommand of(
            UUID userId,
            UUID bookListId
    ) {
        return new DeleteBookListCommand(
                userId,
                bookListId
        );
    }
}
