package com.vellumhub.kafka.contracts.engagement;

import java.time.Instant;
import java.util.UUID;

public record ReactionChangedEvent(
        UUID eventId,
        Instant occurredAt,
        Long reactionId,
        UUID userId,
        UUID bookId,
        String oldTypeReaction,
        String newTypeReaction,
        String typeReaction
) {

    public ReactionChangedEvent(
            UUID eventId,
            Instant occurredAt,
            Long reactionId,
            UUID userId,
            UUID bookId,
            String oldTypeReaction,
            String newTypeReaction
    ) {
        this(
                eventId,
                occurredAt,
                reactionId,
                userId,
                bookId,
                oldTypeReaction,
                newTypeReaction,
                newTypeReaction
        );
    }

    /**
     * Backward-compatible constructor for the legacy payload shape.
     *
     * <p>New producers should use the lifecycle constructor with explicit
     * {@code oldTypeReaction} and {@code newTypeReaction}.</p>
     */
    @Deprecated(forRemoval = true)
    public ReactionChangedEvent(UUID userId, UUID bookId, String typeReaction) {
        this(
                null,
                null,
                null,
                userId,
                bookId,
                null,
                typeReaction,
                typeReaction
        );
    }

    /**
     * Resolves the resulting reaction type across both the enriched and legacy payload shapes.
     */
    public String resultingTypeReaction() {
        return newTypeReaction != null ? newTypeReaction : typeReaction;
    }
}
