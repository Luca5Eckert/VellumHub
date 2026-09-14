package com.vellumhub.testing.distributed.fixture;

import com.vellumhub.kafka.contracts.book.CreateBookEvent;

import java.util.List;
import java.util.UUID;

public final class BookEventFixtures {

    private BookEventFixtures() {
    }

    public static CreateBookEvent createdBook(UUID bookId) {
        return new CreateBookEvent(
                bookId,
                "Distributed Systems in Practice",
                "A deterministic distributed-test fixture",
                2026,
                "https://example.test/covers/distributed-systems.jpg",
                "VellumHub",
                List.of("distributed-systems", "testing")
        );
    }
}
