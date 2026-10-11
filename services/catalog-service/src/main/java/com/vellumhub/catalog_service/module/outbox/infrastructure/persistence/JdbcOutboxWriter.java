package com.vellumhub.catalog_service.module.outbox.infrastructure.persistence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcOutboxWriter implements OutboxWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JdbcOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }
    @Override public void append(OutboxMessage message) {
        final String json;
        try { json = mapper.writeValueAsString(message.payload()); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid outbox payload", ex); }
        jdbc.update("""
            INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                                      event_type, topic, message_key, payload)
            VALUES (?, 'BOOK_PROGRESS', ?, ?, ?, ?, ?, CAST(? AS jsonb))
            """, message.eventId(), message.aggregateId(), message.revision(),
                message.eventType(), message.topic(), message.messageKey(), json);
    }
}

