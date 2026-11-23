package com.vellumhub.kafka.avro;

import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserPreferenceAvroMapperTest {
    @Test
    void preservesTheSnapshotAndRejectsTheWrongKafkaKey() {
        var event = new UserPreferenceChanged(UUID.randomUUID(), Instant.EPOCH, UUID.randomUUID(), 0,
                List.of("Fantasy", "Science Fiction"), null);

        var wire = UserPreferenceAvroMapper.toAvro(event);

        assertEquals(event, UserPreferenceAvroMapper.fromAvro(event.userId().toString(), wire));
        assertThrows(IllegalArgumentException.class,
                () -> UserPreferenceAvroMapper.fromAvro(UUID.randomUUID().toString(), wire));
    }
}
