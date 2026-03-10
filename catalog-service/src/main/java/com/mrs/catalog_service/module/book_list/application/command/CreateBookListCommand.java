package com.mrs.catalog_service.module.book_list.application.command;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateBookListCommand(
        UUID userId,
        List<UUID> books
) {
    public static Object of(@NotEmpty List<UUID> books, UUID userId) {
        return new CreateBookListCommand(userId, books);
    }
}
