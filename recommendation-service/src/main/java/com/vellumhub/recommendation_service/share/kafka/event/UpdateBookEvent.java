package com.vellumhub.recommendation_service.share.kafka.event;

import java.util.List;
import java.util.UUID;

public record UpdateBookEvent(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<String> genres
) {
}
