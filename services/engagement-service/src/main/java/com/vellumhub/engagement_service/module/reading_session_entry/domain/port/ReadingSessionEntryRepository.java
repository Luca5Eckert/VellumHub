package com.vellumhub.engagement_service.module.reading_session_entry.domain.port;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;

public interface ReadingSessionEntryRepository {
    void save(ReadingSessionEntry readingSessionEntry);
}
