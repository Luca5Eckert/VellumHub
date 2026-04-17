package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReadingSessionEntryRepository extends JpaRepository<ReadingSessionEntry, Long> {
}
