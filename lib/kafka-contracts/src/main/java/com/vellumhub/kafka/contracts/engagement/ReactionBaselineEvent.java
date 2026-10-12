package com.vellumhub.kafka.contracts.engagement;
import java.time.Instant;
import java.util.UUID;
/** Cutover checkpoint only; an existing Reaction must not be re-applied to a profile. */
public record ReactionBaselineEvent(UUID eventId, Instant occurredAt, Long reactionId,
                                    UUID userId, UUID bookId, long revision) {}

