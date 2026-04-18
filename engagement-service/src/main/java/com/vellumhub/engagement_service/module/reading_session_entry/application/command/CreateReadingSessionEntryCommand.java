package com.vellumhub.engagement_service.module.reading_session_entry.application.command;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionType;

import java.util.UUID;

public record CreateReadingSessionEntryCommand(
    UUID bookId,
    ReadingSessionType readingSessionType,
    int pageRead
) {
    public static CreateReadingSessionEntryCommand create(UUID bookId, ReadingSessionType type, int pageRead) {
        return new CreateReadingSessionEntryCommand(
                bookId,
                type,
                pageRead
        );
    }
}
