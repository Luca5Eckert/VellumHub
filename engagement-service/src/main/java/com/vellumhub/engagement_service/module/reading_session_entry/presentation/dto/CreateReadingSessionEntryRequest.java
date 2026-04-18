package com.vellumhub.engagement_service.module.reading_session_entry.presentation.dto;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionType;

import java.util.UUID;

public record CreateReadingSessionEntryRequest(
        UUID bookId,
        ReadingSessionType type
) {
}
