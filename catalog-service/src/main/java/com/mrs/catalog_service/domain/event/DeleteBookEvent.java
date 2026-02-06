package com.mrs.catalog_service.domain.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
