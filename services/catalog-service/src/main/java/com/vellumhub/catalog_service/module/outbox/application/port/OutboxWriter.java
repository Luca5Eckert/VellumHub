package com.vellumhub.catalog_service.module.outbox.application.port;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
public interface OutboxWriter { void append(OutboxMessage message); }

