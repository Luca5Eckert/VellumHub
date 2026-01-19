package com.mrs.catalog_service.domain.port;

public interface EventProducer<T,K> {

    void send(String topic, T key, K value);

}
