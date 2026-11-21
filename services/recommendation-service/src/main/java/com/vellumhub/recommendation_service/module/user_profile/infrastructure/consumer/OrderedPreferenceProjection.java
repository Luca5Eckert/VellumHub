package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreateUserProfileUseCase;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderedPreferenceProjection {
    private final JdbcClient jdbc;
    private final CreateUserProfileUseCase profiles;

    OrderedPreferenceProjection(JdbcClient jdbc, CreateUserProfileUseCase profiles) {
        this.jdbc = jdbc;
        this.profiles = profiles;
    }

    @Transactional
    public boolean apply(UserPreferenceChanged event) {
        jdbc.sql("""
                INSERT INTO preference_event_offsets(user_id, last_revision) VALUES (:user, -1)
                ON CONFLICT (user_id) DO NOTHING
                """).param("user", event.userId()).update();

        long lastRevision = jdbc.sql("""
                SELECT last_revision FROM preference_event_offsets WHERE user_id = :user FOR UPDATE
                """).param("user", event.userId()).query(Long.class).single();

        if (!isNextRevision(event, lastRevision)) {
            return false;
        }

        // The unique event ID also rejects reusing a prior identity on a different user or revision.
        jdbc.sql("""
                INSERT INTO processed_preference_events(user_id, revision, event_id)
                VALUES (:user, :revision, :event)
                """).param("user", event.userId()).param("revision", event.revision())
                .param("event", event.eventId()).update();

        profiles.execute(CreatedUserProfileCommand.of(event.userId(), event.genres(), event.about()));

        jdbc.sql("""
                UPDATE preference_event_offsets SET last_revision = :revision WHERE user_id = :user
                """).param("user", event.userId()).param("revision", event.revision()).update();
        return true;
    }

    private boolean isNextRevision(UserPreferenceChanged event, long lastRevision) {
        if (event.revision() <= lastRevision) {
            var previous = jdbc.sql("""
                    SELECT event_id FROM processed_preference_events WHERE user_id = :user AND revision = :revision
                    """).param("user", event.userId()).param("revision", event.revision()).query(UUID.class).optional();
            if (previous.filter(event.eventId()::equals).isPresent()) {
                return false;
            }
            throw new IllegalStateException("Preference revision has a different event identity");
        }
        if (event.revision() != lastRevision + 1) {
            throw new IllegalStateException("Preference revision gap");
        }
        return true;
    }
}
