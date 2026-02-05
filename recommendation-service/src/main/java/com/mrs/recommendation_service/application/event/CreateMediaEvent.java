package com.mrs.recommendation_service.application.event;

import com.mrs.recommendation_service.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record CreateMediaEvent(
        UUID mediaId,
        List<Genre> genres
) {
}
