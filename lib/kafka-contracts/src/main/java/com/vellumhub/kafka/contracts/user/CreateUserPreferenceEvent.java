package com.vellumhub.kafka.contracts.user;

import java.util.List;
import java.util.UUID;

public record CreateUserPreferenceEvent(
        UUID userId,
        List<String> genres,
        String about
) {
}
