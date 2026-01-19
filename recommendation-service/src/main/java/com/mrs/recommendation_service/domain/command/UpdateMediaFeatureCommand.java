package com.mrs.recommendation_service.domain.command;

import java.util.List;
import java.util.UUID;

public record UpdateMediaFeatureCommand(
        UUID mediaId,
        List<String> genres
) {
}
