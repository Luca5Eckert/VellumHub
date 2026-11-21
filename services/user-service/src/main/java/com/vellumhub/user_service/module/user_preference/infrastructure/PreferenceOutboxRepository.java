package com.vellumhub.user_service.module.user_preference.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PreferenceOutboxRepository extends JpaRepository<PreferenceOutboxEvent, UUID> {
    @Query(value = """
            SELECT candidate.* FROM user_preference_outbox candidate
            WHERE candidate.next_attempt_at <= CURRENT_TIMESTAMP
              AND NOT EXISTS (
                SELECT 1 FROM user_preference_outbox pending
                WHERE pending.user_id = candidate.user_id AND pending.revision < candidate.revision
              )
            ORDER BY candidate.next_attempt_at, candidate.event_id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<PreferenceOutboxEvent> lockNext();
}
