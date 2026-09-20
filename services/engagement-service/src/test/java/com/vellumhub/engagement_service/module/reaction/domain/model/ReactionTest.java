package com.vellumhub.engagement_service.module.reaction.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionTest {

    @Test
    void occurrenceTimesShouldMatchPostgresMicrosecondPrecision() {
        UUID owner = UUID.randomUUID();
        Reaction reaction = Reaction.of(owner, new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE, Instant.parse("2026-09-19T12:00:00.123456789Z"));
        Instant createdAt = Instant.parse("2026-09-19T12:00:00.123456Z");
        assertThat(reaction.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reaction.getUpdatedAt()).isEqualTo(createdAt);

        reaction.updateType(TypeReaction.NEGATIVE, owner, Instant.parse("2026-09-19T12:01:00.987654999Z"));
        assertThat(reaction.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reaction.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-19T12:01:00.987654Z"));
    }

    @Test
    void creationShouldInitializeAuditTimestampsFromOccurrenceTime() {
        Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");

        Reaction reaction = Reaction.of(
                UUID.randomUUID(),
                new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE,
                occurredAt
        );

        assertThat(reaction.getCreatedAt()).isEqualTo(occurredAt);
        assertThat(reaction.getUpdatedAt()).isEqualTo(occurredAt);
    }

    @Test
    void updateShouldPreserveCreationTimeAndAdvanceUpdateTime() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(30);
        Reaction reaction = Reaction.of(
                userId,
                new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE,
                createdAt
        );

        TypeReaction previous = reaction.updateType(TypeReaction.VERY_POSITIVE, userId, updatedAt);

        assertThat(previous).isEqualTo(TypeReaction.POSITIVE);
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE);
        assertThat(reaction.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reaction.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void unchangedTypeShouldStillRecordTheOccurrenceWithoutInventingATransition() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(30);
        Reaction reaction = Reaction.of(
                userId,
                new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE,
                createdAt
        );

        TypeReaction previous = reaction.updateType(TypeReaction.POSITIVE, userId, updatedAt);

        assertThat(previous).isEqualTo(TypeReaction.POSITIVE);
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
        assertThat(reaction.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateShouldRejectAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        Reaction reaction = Reaction.of(
                ownerId,
                new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE,
                Instant.parse("2026-09-19T12:00:00Z")
        );

        assertThatThrownBy(() -> reaction.updateType(
                TypeReaction.NEGATIVE,
                UUID.randomUUID(),
                Instant.parse("2026-09-19T12:01:00Z")
        ))
                .isInstanceOf(ReactionException.class)
                .hasMessage("User cannot update reaction of another user");
    }

    @Test
    void reactionOccurrenceTimeShouldBeRequired() {
        assertThatThrownBy(() -> Reaction.of(
                UUID.randomUUID(),
                new BookSnapshot(UUID.randomUUID()),
                TypeReaction.POSITIVE,
                null
        ))
                .isInstanceOf(ReactionException.class)
                .hasMessage("Reaction occurrence time cannot be null");
    }
}
