package com.vellumhub.catalog_service.module.outbox.application.model;
import java.util.UUID;
public record OutboxRecord(UUID eventId, UUID claimToken, String topic, String messageKey,
                           String payload, int attempts) {}

