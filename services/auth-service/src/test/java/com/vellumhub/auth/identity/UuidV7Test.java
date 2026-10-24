package com.vellumhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class UuidV7Test {
    @Test
    void generatesRfc9562UuidV7WithExpectedTimestampAndUniqueness() {
        var fixed = Instant.parse("2026-10-23T21:00:00Z");
        var generator = new UuidV7(new SecureRandom(), Clock.fixed(fixed, ZoneOffset.UTC));
        var ids = new HashSet<String>();
        for (int i = 0; i < 10_000; i++) {
            var id = generator.next();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(fixed.toEpochMilli());
            ids.add(id.toString());
        }
        assertThat(ids).hasSize(10_000);
    }
}
