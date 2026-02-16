package com.mrs.recommendation_service.module.book_feature.domain.command;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookFeatureCommand(
        UUID mediaId,
        List<Genre> genres
) {
}
