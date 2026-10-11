package com.vellumhub.kafka.contracts.readingprogress;
import java.time.Instant;
import java.util.UUID;

/** Compatibility DTO; revision is populated for new Catalog writes. */
public record UpdateBookProgressEvent(
  UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId, UUID bookId,
  String progress, int oldPage, int newPage, long revision
) {
  public UpdateBookProgressEvent(UUID eventId, Instant occurredAt, UUID bookProgressId, UUID userId,
                                 UUID bookId, String progress, int oldPage, int newPage) {
    this(eventId, occurredAt, bookProgressId, userId, bookId, progress, oldPage, newPage, -1L);
  }
  public UpdateBookProgressEvent(UUID bookProgressId, UUID userId, UUID bookId, String progress,
                                 int oldPage, int newPage) {
    this(null, null, bookProgressId, userId, bookId, progress, oldPage, newPage, -1L);
  }
}

