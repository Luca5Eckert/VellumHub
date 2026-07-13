package com.vellumhub.kafka.contracts.readingprogress;

import java.util.UUID;

public record UpdateBookProgressEvent(
        UUID bookProgressId,
        UUID userId,
        UUID bookId,
        String progress,
        int oldPage,
        int newPage
) {
}
