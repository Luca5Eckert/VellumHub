package com.mrs.recommendation_service.application.event;

import java.util.List;
import java.util.UUID;

public record UpdateMediaEvent(
        UUID mediaId,
        List<String> genres
) {
}

