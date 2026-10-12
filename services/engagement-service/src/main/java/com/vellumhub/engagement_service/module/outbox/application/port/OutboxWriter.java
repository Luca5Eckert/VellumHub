package com.vellumhub.engagement_service.module.outbox.application.port;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;

/** Persists event data in the transaction owned by the calling use case. */
public interface OutboxWriter { void append(OutboxMessage message); }

