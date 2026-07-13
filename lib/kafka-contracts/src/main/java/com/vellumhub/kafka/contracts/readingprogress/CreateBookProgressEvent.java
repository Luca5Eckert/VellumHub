package com.vellumhub.kafka.contracts.readingprogress;

import java.util.UUID;

public record CreateBookProgressEvent(
        UUID bookProgressId,
        UUID userId,
        UUID bookId,
        String progress,
        int initPage
) {
}
