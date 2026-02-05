package com.mrs.recommendation_service.domain.command;

import com.mrs.recommendation_service.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateMediaFeatureCommand(
        UUID mediaId,
        List<Genre> genres
) {
}
