package com.vellumhub.engagement_service.module.outbox.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.port.OutboxWriter;
import com.vellumhub.engagement_service.module.outbox.application.exception.EngagementWritesFrozenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Inserts directly into the caller's PostgreSQL transaction; failures abort the domain change. */
@Repository
public class JdbcOutboxWriter implements OutboxWriter {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    public JdbcOutboxWriter(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override @Transactional public void append(OutboxMessage message) {
        // Writer gate holds FOR SHARE until the aggregate+Outbox transaction commits.
        // Operators freezing a stream wait for in-flight mutations across replicas.
        Boolean frozen = jdbc.queryForObject(
                "SELECT frozen FROM engagement_write_gate WHERE aggregate_type=? FOR SHARE",
                Boolean.class,message.aggregateType());
        if (frozen == null) throw new IllegalStateException("Missing Engagement cutover gate");
        if (frozen) throw new EngagementWritesFrozenException(message.aggregateType());
        final String json;
        try {json=mapper.writeValueAsString(message.payload());}
        catch(JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid integration event type="+message.eventType(), error);
        }
        jdbc.update("""
            INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                                      event_type, topic, message_key, payload,
                                      wire_format, payload_schema_version)
            VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),?,?)
            """,message.eventId(),message.aggregateType(),message.aggregateId(),message.revision(),
                message.eventType(),message.topic(),message.key(),json,
                message.wireFormat(),message.payloadSchemaVersion());
    }
}

