package com.vellumhub.engagement_service.module.reading_session_entry.presentation.dto;

import java.util.UUID;

public record CreateReadingSessionEntryRequest(
        UUID bookId,
        ReadingSessionType type,
        int pageRead
) {
}
