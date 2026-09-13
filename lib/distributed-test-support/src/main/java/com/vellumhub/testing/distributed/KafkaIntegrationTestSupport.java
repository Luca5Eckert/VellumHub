package com.vellumhub.testing.distributed;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Testcontainers
public abstract class KafkaIntegrationTestSupport {

    protected static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);
    protected static final Duration ASYNC_POLL_INTERVAL = Duration.ofMillis(100);

    @Container
    protected static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
}
