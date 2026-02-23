package com.mrs.engagement_service.module.rating.domain.command;

public record GetAllRatingByBookCommand(
        int bookId,
        int page,
        int size
) {
}
