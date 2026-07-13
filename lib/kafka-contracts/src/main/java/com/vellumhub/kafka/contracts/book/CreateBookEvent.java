package com.vellumhub.kafka.contracts.book;

import java.util.List;
import java.util.UUID;

public record CreateBookEvent(
        UUID bookId,
        String title,
        String description,
        int releaseYear,
        String coverUrl,
        String author,
        List<String> genres
) {
}
