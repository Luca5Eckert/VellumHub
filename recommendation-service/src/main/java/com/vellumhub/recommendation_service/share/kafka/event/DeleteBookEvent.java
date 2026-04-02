package com.vellumhub.recommendation_service.share.kafka.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
