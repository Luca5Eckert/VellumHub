-- #272: durable recovery markers. The source Kafka offset is NOT committed on quarantine.
CREATE TABLE kafka_consumer_quarantine (
    consumer_group VARCHAR(160) NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    event_id UUID,
    failure_category VARCHAR(48) NOT NULL,
    attempts INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'BLOCKED',
    dlt_topic VARCHAR(255) NOT NULL,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_group, source_topic, source_partition, source_offset),
    CONSTRAINT ck_quarantine_state CHECK (status IN ('BLOCKED', 'RETRY_REQUESTED', 'RESOLVED')),
    CONSTRAINT ck_quarantine_attempts CHECK (attempts > 0)
);
CREATE INDEX idx_kafka_quarantine_active ON kafka_consumer_quarantine
    (consumer_group, status, source_topic, source_partition)
    WHERE status <> 'RESOLVED';

CREATE TABLE kafka_recovery_audit (
    id BIGSERIAL PRIMARY KEY,
    operation VARCHAR(48) NOT NULL,
    consumer_group VARCHAR(160) NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    operator_id VARCHAR(160) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
