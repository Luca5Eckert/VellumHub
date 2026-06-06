package com.vellumhub.engagement_service.module.book_snapshot.presentation.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
