package com.vellumhub.catalog_service.module.book_list.application.command.member;

import java.util.UUID;

public record RemoveBookInListCommand(
        UUID userId,
        UUID listId,
        UUID bookId
) {
}
