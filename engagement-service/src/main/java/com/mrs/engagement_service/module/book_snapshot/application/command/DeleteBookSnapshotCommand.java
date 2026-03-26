package com.mrs.engagement_service.module.book_snapshot.application.command;

import java.util.Objects;
import java.util.UUID;

public record DeleteBookSnapshotCommand(
        UUID bookId
) {

    public DeleteBookSnapshotCommand {
        Objects.requireNonNull(bookId, "bookId must not be null");
    }
}
