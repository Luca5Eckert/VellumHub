package com.vellumhub.engagement_service.module.outbox.application.model;
import java.util.UUID;

/** A leased message: claimToken fences stale workers during ACK/retry. */
public record OutboxRecord(UUID eventId, UUID claimToken, String aggregateType,
                           String eventType, String topic, String key, String json, int attempts) {}

