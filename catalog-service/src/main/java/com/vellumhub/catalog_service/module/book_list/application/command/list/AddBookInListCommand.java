package com.vellumhub.catalog_service.module.book_list.application.command.list;

import java.util.UUID;

public record AddBookInListCommand(
        UUID userId,
        UUID listId,
        UUID bookId
) {
    public static AddBookInListCommand of(UUID userId, UUID bookListId, UUID bookId) {
        return new AddBookInListCommand(userId, bookListId, bookId);
    }
}
