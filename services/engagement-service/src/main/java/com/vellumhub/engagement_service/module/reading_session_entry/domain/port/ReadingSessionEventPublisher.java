package com.vellumhub.engagement_service.module.reading_session_entry.domain.port;

public interface ReadingSessionEventPublisher<K, E> {

    void publish(String topic, K key, E event);

}
