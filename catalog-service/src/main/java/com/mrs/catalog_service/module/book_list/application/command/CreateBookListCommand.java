package com.mrs.catalog_service.module.book_list.application.command;

import java.util.List;
import java.util.UUID;

public record CreateBookListCommand(
        UUID userId,
        List<UUID> books
) {
}
