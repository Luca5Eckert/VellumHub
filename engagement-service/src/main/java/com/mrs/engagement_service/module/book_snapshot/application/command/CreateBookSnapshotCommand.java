package com.mrs.engagement_service.module.book_snapshot.application.command;

import java.util.Objects;
import java.util.UUID;

public record CreateBookSnapshotCommand(
        UUID bookId
) {

    public CreateBookSnapshotCommand {
        Objects.requireNonNull(bookId, "bookId must not be null");
    }

}
