package com.vellumhub.auth.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** RFC 9562 UUIDv7 for NEW canonical user identities. Existing UUIDs remain unchanged. */
public final class UuidV7 {
    private final SecureRandom random;
    private final Clock clock;

    public UuidV7(SecureRandom random, Clock clock) {
        this.random = random;
        this.clock = clock;
    }

    public UUID next() {
        // Unix timestamp in milliseconds (48 bits), version 7, 74 random bits and RFC variant.
        long milliseconds = clock.millis();
        long most = ((milliseconds & 0x0000_FFFF_FFFF_FFFFL) << 16)
                | 0x0000_0000_0000_7000L | (random.nextInt(4096) & 0x0FFFL);
        long least = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL)
                | 0x8000_0000_0000_0000L;
        return new UUID(most, least);
    }
}
