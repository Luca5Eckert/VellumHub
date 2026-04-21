package com.vellumhub.engagement_service.module.reading_session_entry.application.command;

import java.util.UUID;

public record CreateReadingSessionEntryCommand(
    UUID bookId,
    UUID bookProgressId,
    String type,
    int pageRead
) {
    public static CreateReadingSessionEntryCommand create(UUID bookId, UUID bookProgressId, String type, int pageRead) {
        return new CreateReadingSessionEntryCommand(
                bookId,
                bookProgressId,
                type,
                pageRead
        );
    }
}
