package com.vellumhub.recommendation_service.share.application;

/** Durable outcome of a consumer transaction, distinct from physical Kafka delivery. */
public enum EventProcessingOutcome {
    APPLIED,
    DUPLICATE
}
