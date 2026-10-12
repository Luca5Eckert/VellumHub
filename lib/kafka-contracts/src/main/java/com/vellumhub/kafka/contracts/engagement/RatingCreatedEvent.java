package com.vellumhub.kafka.contracts.engagement;
import java.time.Instant;
import java.util.UUID;

/** A rating came into existence; facts are named in the past tense. */
public record RatingCreatedEvent(UUID eventId,Instant occurredAt,Long ratingId,UUID userId,
                                 UUID bookId,int stars,long revision) {}

