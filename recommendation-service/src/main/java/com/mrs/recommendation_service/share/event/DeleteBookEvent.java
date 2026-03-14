package com.mrs.recommendation_service.share.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
