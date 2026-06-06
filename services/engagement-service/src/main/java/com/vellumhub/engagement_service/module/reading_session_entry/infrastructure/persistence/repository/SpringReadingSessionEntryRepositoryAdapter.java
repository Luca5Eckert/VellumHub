package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import org.springframework.stereotype.Repository;

@Repository
public class SpringReadingSessionEntryRepositoryAdapter implements ReadingSessionEntryRepository {

    private final JpaReadingSessionEntryRepository jpaReadingSessionEntryRepository;

    public SpringReadingSessionEntryRepositoryAdapter(JpaReadingSessionEntryRepository jpaReadingSessionEntryRepository) {
        this.jpaReadingSessionEntryRepository = jpaReadingSessionEntryRepository;
    }

    @Override
    public void save(ReadingSessionEntry readingSessionEntry) {
        jpaReadingSessionEntryRepository.save(readingSessionEntry);
    }
}
