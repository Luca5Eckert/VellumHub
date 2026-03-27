package com.mrs.catalog_service.module.book.domain.port;

public interface BookEventProducer<K, V> {

    void send(String topic, K key, V value);

}
