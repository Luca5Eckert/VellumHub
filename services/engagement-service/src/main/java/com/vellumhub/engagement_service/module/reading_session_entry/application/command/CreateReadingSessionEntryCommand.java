package com.vellumhub.engagement_service.module.reading_session_entry.application.command;

import java.time.Instant;
import java.util.UUID;

public record CreateReadingSessionEntryCommand(
    UUID bookId, UUID bookProgressId, UUID userId, String type, int pageRead,
    UUID eventId, Instant occurredAt
) {
    public CreateReadingSessionEntryCommand(UUID bookId, UUID bookProgressId, UUID userId, String type, int pageRead) {
        this(bookId, bookProgressId, userId, type, pageRead, null, null);
    }

    public static CreateReadingSessionEntryCommand create(UUID bookId, UUID bookProgressId, UUID userId, String type, int pageRead) {
        return new CreateReadingSessionEntryCommand(bookId, bookProgressId, userId, type, pageRead);
    }

    public static CreateReadingSessionEntryCommand create(UUID bookId, UUID bookProgressId, UUID userId, String type, int pageRead, UUID eventId, Instant occurredAt) {
        return new CreateReadingSessionEntryCommand(bookId, bookProgressId, userId, type, pageRead, eventId, occurredAt);
    }
}
