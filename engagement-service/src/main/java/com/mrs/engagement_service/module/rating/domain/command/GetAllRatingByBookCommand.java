package com.mrs.engagement_service.module.rating.domain.command;

import java.util.UUID;

public record GetAllRatingByBookCommand(
        UUID bookId,
        int page,
        int size
) {
    public static GetAllRatingByBookCommand of(UUID bookId, int page, int size) {
        return new GetAllRatingByBookCommand(
                bookId,
                page,
                size
        );
    }
}
