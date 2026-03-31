package com.vellumhub.catalog_service.module.book_list.application.command.list;

import java.util.UUID;

public record AddBookInListCommand(
        UUID listId,
        UUID bookId
) {
}
