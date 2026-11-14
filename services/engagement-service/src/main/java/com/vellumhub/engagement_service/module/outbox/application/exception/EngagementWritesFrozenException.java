package com.vellumhub.engagement_service.module.outbox.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.SERVICE_UNAVAILABLE, reason="Engagement writes temporarily frozen")
public final class EngagementWritesFrozenException extends RuntimeException {
    public EngagementWritesFrozenException(String stream) { super(stream + " writes temporarily frozen"); }
}
