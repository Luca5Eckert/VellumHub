package com.mrs.catalog_service.domain.port;

public interface EventProducer<K, V> {

    void send(String topic, K key, V value);

}
