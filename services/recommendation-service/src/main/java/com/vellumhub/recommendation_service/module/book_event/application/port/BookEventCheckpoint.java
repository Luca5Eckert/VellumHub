package com.vellumhub.recommendation_service.module.book_event.application.port;
import java.util.UUID;

/** Durable per-book consumption sequence, with a row lock held until the effect commits. */
public interface BookEventCheckpoint {
    long lockAndRead(UUID bookId);
    void applied(UUID bookId, long previousRevision, long newRevision);
}

