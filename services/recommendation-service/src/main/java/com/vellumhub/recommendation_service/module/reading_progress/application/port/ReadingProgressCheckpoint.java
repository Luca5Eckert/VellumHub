package com.vellumhub.recommendation_service.module.reading_progress.application.port;
import java.util.UUID;
public interface ReadingProgressCheckpoint {
    long lockAndRead(UUID aggregateId);
    void markApplied(UUID aggregateId, long revision, UUID eventId);
}

