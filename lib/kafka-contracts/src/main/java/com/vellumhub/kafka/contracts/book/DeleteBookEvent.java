package com.vellumhub.kafka.contracts.book;

import java.util.UUID;

public record DeleteBookEvent(
        UUID bookId
) {
}
