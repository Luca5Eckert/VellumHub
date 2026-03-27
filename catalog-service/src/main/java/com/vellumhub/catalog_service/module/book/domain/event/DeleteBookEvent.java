package com.vellumhub.catalog_service.module.book.domain.event;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
