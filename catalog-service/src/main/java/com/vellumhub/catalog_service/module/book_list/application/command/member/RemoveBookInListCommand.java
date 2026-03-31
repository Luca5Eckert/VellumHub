package com.vellumhub.catalog_service.module.book_list.application.command.member;

import java.util.UUID;

public record RemoveBookInListCommand(
        UUID userId,
        UUID listId,
        UUID bookId
) {
    public static RemoveBookInListCommand of(UUID userId, UUID bookListId, UUID bookId) {
        return new RemoveBookInListCommand(userId, bookListId, bookId);
    }
}
