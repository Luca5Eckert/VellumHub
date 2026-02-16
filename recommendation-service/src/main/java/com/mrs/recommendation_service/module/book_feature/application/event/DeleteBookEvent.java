package com.mrs.recommendation_service.module.book_feature.application.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
