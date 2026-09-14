package com.vellumhub.testing.distributed.container;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class KafkaPgvectorIntegrationTestSupport extends KafkaIntegrationTestSupport {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("vellumhub_distributed_test")
            .withUsername("test")
            .withPassword("test");
}
