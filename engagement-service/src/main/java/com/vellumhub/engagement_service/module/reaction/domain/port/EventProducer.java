package com.vellumhub.engagement_service.module.reaction.domain.port;

public interface EventProducer<K, E> {
    void send(String topic, K key, E event);
}
