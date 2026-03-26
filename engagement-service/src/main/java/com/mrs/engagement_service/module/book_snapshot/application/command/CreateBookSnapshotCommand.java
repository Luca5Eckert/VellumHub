package com.mrs.engagement_service.module.book_snapshot.application.command;

import java.util.UUID;

public record CreateBookSnapshotCommand(
        UUID bookId
) {
}
