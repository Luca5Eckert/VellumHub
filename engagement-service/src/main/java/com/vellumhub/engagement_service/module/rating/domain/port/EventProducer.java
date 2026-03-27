package com.vellumhub.engagement_service.module.rating.domain.port;

public interface EventProducer<K, V> {
    void send(String topic, K key, V value);
}
