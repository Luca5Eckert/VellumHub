package com.vellumhub.kafka.contracts.engagement;

import java.util.UUID;

public record ReactionChangedEvent(
        UUID userId,
        UUID bookId,
        String typeReaction
) {
}
