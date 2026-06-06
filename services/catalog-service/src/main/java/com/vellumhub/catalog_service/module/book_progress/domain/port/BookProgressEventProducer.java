package com.vellumhub.catalog_service.module.book_progress.domain.port;

public interface BookProgressEventProducer<K, V> {

    void send(String topic, K key, V value);

}